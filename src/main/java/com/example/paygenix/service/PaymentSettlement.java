package com.example.paygenix.service;

import com.example.paygenix.config.FeeProperties;
import com.example.paygenix.dto.response.PaymentResponse;
import com.example.paygenix.entity.Cards;
import com.example.paygenix.entity.Payment;
import com.example.paygenix.entity.PaymentOnboarding;
import com.example.paygenix.entity.PaymentTransaction;
import com.example.paygenix.entity.User;
import com.example.paygenix.enums.Currency;
import com.example.paygenix.enums.LedgerKind;
import com.example.paygenix.enums.PaymentStatus;
import com.example.paygenix.enums.SystemAccount;
import com.example.paygenix.exceptions.ApiException;
import com.example.paygenix.exceptions.InsufficientBalanceException;
import com.example.paygenix.exceptions.NotFoundException;
import com.example.paygenix.exceptions.PaymentAlreadyProcessedException;
import com.example.paygenix.mapper.PaymentMapper;
import com.example.paygenix.repository.CardRepository;
import com.example.paygenix.repository.PaymentOnboardingRepository;
import com.example.paygenix.repository.PaymentRepository;
import com.example.paygenix.repository.PaymentTransactionRepository;
import com.example.paygenix.service.LedgerService.Leg;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The three committed steps of a phone payment (T-144). Between {@link #reserve} and {@link #settle} or
 * {@link #release} the operator is called with no transaction open and no row locked; the held amount sits in
 * PAYMENT_HOLD, so the card cannot spend it twice and the books balance at every moment. Settle and release lock the
 * flow row and act only on a PROCESSING flow, so the request and {@link PaymentReconciliation} can race safely.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentSettlement {

    /** What is sent to the operator; the reference identifies this one reservation. */
    public record Reservation(String reference, String phoneNumber, BigDecimal amount, Currency currency) {
    }

    private final PaymentOnboardingRepository flows;
    private final CardRepository cardRepository;
    private final CardService cardService;
    private final PinValidationService pinValidationService;
    private final LegalAcceptanceService legalAcceptanceService;
    private final LedgerService ledgerService;
    private final FeeProperties fees;
    private final PaymentTransactionRepository ptRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentMapper paymentMapper;
    private final MeterRegistry meterRegistry;

    /**
     * Checks the flow, the PIN and the balance, and holds the amount. A flow that is already held (an earlier answer
     * was lost) is not held again: the same reservation comes back, so the operator sees the same reference.
     */
    @Transactional
    public Reservation reserve(String flowId, String pin, User payer) {
        legalAcceptanceService.requireAccepted(payer.getId());
        // someone else's flow gets the same 404 as an unknown id; the row lock queues two confirms of one flow
        PaymentOnboarding flow = flows.findByIdAndUserId(flowId, payer.getId())
                .orElseThrow(() -> new NotFoundException("Payment not found"));
        // a completed payment stays "already processed" however late the retry: "start again" would make the user pay twice
        if (flow.getPaymentStatus() == PaymentStatus.COMPLETED) {
            throw new PaymentAlreadyProcessedException();
        }
        if (flow.getPaymentStatus() == PaymentStatus.PROCESSING) {
            pinValidationService.validatePin(payer.getId(), pin);
            return reservationOf(flow);
        }
        if (flow.getPaymentStatus() == PaymentStatus.EXPIRED || PaymentService.isExpired(flow, Instant.now())) {
            throw new ApiException(HttpStatus.GONE, "PAYMENT_EXPIRED", "This payment was not confirmed in time; start again");
        }

        pinValidationService.validatePin(payer.getId(), pin);

        Cards from = cardRepository.findByIdAndOwnerId(flow.getFromCardId(), payer.getId())
                .orElseThrow(() -> new NotFoundException("Card not found"));
        // the balance is read only after the row is locked, so concurrent debits cannot overspend
        cardService.lockForUpdate(from);
        if (from.getAmount().compareTo(flow.getAmount()) < 0) {
            throw new InsufficientBalanceException("Insufficient balance");
        }

        String reference = newReference();
        ledgerService.post(LedgerKind.PAYMENT, reference, from.getCurrency(), List.of(
                Leg.card(from, flow.getAmount().negate()),
                Leg.system(SystemAccount.PAYMENT_HOLD, flow.getAmount())));
        cardRepository.save(from);

        flow.setPaymentStatus(PaymentStatus.PROCESSING);
        flow.setOperatorReference(reference);
        flow.setReservedAt(Instant.now());
        flows.save(flow);
        return reservationOf(flow);
    }

    /**
     * The operator delivered: hold → payout, cashback to the card, the payment recorded. A second call for the same
     * reservation (a parallel confirm, or reconciliation racing a retry) changes nothing and answers
     * PAYMENT_ALREADY_PROCESSED, as a second confirm of a completed flow always has.
     */
    @Transactional
    public PaymentResponse settle(String reference) {
        Optional<PaymentOnboarding> found = flows.findByOperatorReference(reference);
        if (found.isEmpty() || found.get().getPaymentStatus() != PaymentStatus.PROCESSING) {
            if (found.isPresent() && found.get().getPaymentStatus() == PaymentStatus.COMPLETED) {
                throw new PaymentAlreadyProcessedException(); // the other path settled it a moment ago
            }
            // shortcut: delivered after the hold was returned (only if the operator answered later than
            // RECONCILE_AFTER); the phone got the money and the card kept it. Left to a person, not guessed at.
            log.error("Operator delivered top-up {} after its hold was released; needs manual review", reference);
            meterRegistry.counter("paygenix.payments.review", "reason", "delivered_after_release").increment();
            throw new ApiException(HttpStatus.CONFLICT, "PAYMENT_NEEDS_REVIEW",
                    "This payment needs a manual check; nothing more will be charged");
        }
        PaymentOnboarding flow = found.get();
        Cards from = cardRepository.findById(flow.getFromCardId()).orElseThrow();
        cardService.lockForUpdate(from);

        // a card closed while the payment was held gets no cashback (it is booked to a card nobody can use)
        BigDecimal cashback = from.getClosedAt() == null ? fees.cashbackOf(flow.getAmount()) : BigDecimal.ZERO;
        ledgerService.post(LedgerKind.PAYMENT, reference, flow.getCurrency(), List.of(
                Leg.system(SystemAccount.PAYMENT_HOLD, flow.getAmount().negate()),
                Leg.system(SystemAccount.PAYMENT_PAYOUT, flow.getAmount())));
        ledgerService.post(LedgerKind.CASHBACK, reference, flow.getCurrency(), List.of(
                Leg.system(SystemAccount.CASHBACK_EXPENSE, cashback.negate()),
                Leg.card(from, cashback)));
        cardRepository.save(from);

        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setTransactionId(reference);
        transaction.setFrom_card_id(from.getLast4());
        transaction.setAmount(flow.getAmount());
        transaction.setCashbackPercent(fees.cashbackPercent());
        transaction.setCashbackValue(cashback);
        transaction.setCurrency(flow.getCurrency());
        transaction.setStatus(PaymentStatus.COMPLETED);
        transaction.setPhone_number(flow.getPhoneNumber());
        ptRepository.save(transaction);

        Payment payment = paymentRepository.save(paymentOf(flow));
        flow.setPaymentStatus(PaymentStatus.COMPLETED);
        flows.save(flow);
        return paymentMapper.toPaymentResponse(payment);
    }

    /**
     * The operator did not deliver: hold → card, and the flow can be confirmed again (or is expired). Returns false
     * when there was nothing to release (already settled or released).
     */
    @Transactional
    public boolean release(String reference) {
        Optional<PaymentOnboarding> found = flows.findByOperatorReference(reference);
        if (found.isEmpty() || found.get().getPaymentStatus() != PaymentStatus.PROCESSING) {
            return false;
        }
        PaymentOnboarding flow = found.get();
        Cards from = cardRepository.findById(flow.getFromCardId()).orElseThrow();
        cardService.lockForUpdate(from);
        ledgerService.post(LedgerKind.PAYMENT_RELEASE, reference, flow.getCurrency(), List.of(
                Leg.system(SystemAccount.PAYMENT_HOLD, flow.getAmount().negate()),
                Leg.card(from, flow.getAmount())));
        cardRepository.save(from);
        flow.setPaymentStatus(PaymentService.isExpired(flow, Instant.now()) ? PaymentStatus.EXPIRED : PaymentStatus.PENDING);
        flows.save(flow);
        return true;
    }

    private static Reservation reservationOf(PaymentOnboarding flow) {
        return new Reservation(flow.getOperatorReference(), flow.getPhoneNumber(), flow.getAmount(), flow.getCurrency());
    }

    private static Payment paymentOf(PaymentOnboarding flow) {
        return Payment.builder()
                .amount(flow.getAmount())
                .currency(flow.getCurrency())
                .creationDate(Instant.now())
                .fromCardNumber(flow.getFromCardNumber())
                .phoneNumber(flow.getPhoneNumber())
                .build();
    }

    // unique per reservation (V20 unique constraint): a full random UUID, not a short one that could repeat
    private static String newReference() {
        return "PTXN-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-"
                + UUID.randomUUID().toString().replace("-", "").toUpperCase();
    }
}
