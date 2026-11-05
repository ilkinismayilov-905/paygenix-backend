package com.example.paygenix.service;

import com.example.paygenix.config.FeeProperties;
import com.example.paygenix.dto.request.PaymentRequest;
import com.example.paygenix.dto.response.PaymentResponse;
import com.example.paygenix.entity.Cards;
import com.example.paygenix.entity.Payment;
import com.example.paygenix.entity.PaymentOnboarding;
import com.example.paygenix.entity.PaymentTransaction;
import com.example.paygenix.entity.User;
import com.example.paygenix.enums.LedgerKind;
import com.example.paygenix.enums.PaymentStatus;
import com.example.paygenix.enums.SystemAccount;
import com.example.paygenix.exceptions.InsufficientBalanceException;
import com.example.paygenix.exceptions.NotFoundException;
import com.example.paygenix.exceptions.PaymentAlreadyProcessedException;
import com.example.paygenix.mapper.PaymentMapper;
import com.example.paygenix.mapper.PaymentTransactionMapper;
import com.example.paygenix.repository.CardRepository;
import com.example.paygenix.repository.PaymentOnboardingRepository;
import com.example.paygenix.repository.PaymentRepository;
import com.example.paygenix.repository.PaymentTransactionRepository;
import com.example.paygenix.service.LedgerService.Leg;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentTransactionRepository ptRepository;
    private final PaymentTransactionMapper ptMapper;
    private final PaymentMapper paymentMapper;
    private final CardRepository cardRepository;
    private final CardService cardService;
    private final PaymentTransactionService ptService;
    private final PaymentOnboardingRepository poRepository;
    private final PinValidationService pinValidationService;
    private final LedgerService ledgerService;
    private final FeeProperties fees;
    private final LegalAcceptanceService legalAcceptanceService;

    @Transactional
    public String transfer(PaymentRequest req, User payer) {
        legalAcceptanceService.requireAccepted(payer.getId());
        Cards from = cardService.findOwned(payer, req.fromCardId());

        // early rejection only; confirm re-checks the balance under the row lock
        if (from.getAmount().compareTo(req.amount()) < 0) {
            throw new InsufficientBalanceException("Insufficient balance");
        }

        PaymentOnboarding onboarding = PaymentOnboarding.builder()
                .id(UUID.randomUUID().toString())
                .amount(req.amount())
                .currency(from.getCurrency())
                .fromCardId(from.getId())
                .fromCardNumber(from.getLast4())
                .phoneNumber(req.phoneNumber())
                .creationDate(Instant.now())
                .paymentStatus(PaymentStatus.PENDING)
                .userId(payer.getId())
                .build();

        poRepository.save(onboarding);
        return onboarding.getId();
    }

    @Transactional
    public PaymentResponse confirmPayment(String flowId, String pin, User payer) {
        legalAcceptanceService.requireAccepted(payer.getId());
        // someone else's flow gets the same 404 as an unknown id
        PaymentOnboarding onboarding = poRepository.findByIdAndUserId(flowId, payer.getId())
                .orElseThrow(() -> new NotFoundException("Payment not found"));
        if (onboarding.getPaymentStatus() != PaymentStatus.PENDING) {
            throw new PaymentAlreadyProcessedException();
        }

        pinValidationService.validatePin(payer.getId(), pin);

        Cards from = cardRepository.findByIdAndOwnerId(onboarding.getFromCardId(), payer.getId())
                .orElseThrow(() -> new NotFoundException("Card not found"));
        // the balance is read only after the row is locked, so concurrent debits cannot overspend
        cardService.lockForUpdate(from);

        if (from.getAmount().compareTo(onboarding.getAmount()) < 0) {
            throw new InsufficientBalanceException("Insufficient balance");
        }

        PaymentTransaction transaction = new PaymentTransaction();

        BigDecimal cashbackAmount = fees.cashbackOf(onboarding.getAmount());

        transaction.setTransactionId(ptService.generatePaymentTransactionId());
        ledgerService.post(LedgerKind.PAYMENT, transaction.getTransactionId(), from.getCurrency(), List.of(
                Leg.card(from, onboarding.getAmount().negate()),
                Leg.system(SystemAccount.PAYMENT_PAYOUT, onboarding.getAmount())));
        ledgerService.post(LedgerKind.CASHBACK, transaction.getTransactionId(), from.getCurrency(), List.of(
                Leg.system(SystemAccount.CASHBACK_EXPENSE, cashbackAmount.negate()),
                Leg.card(from, cashbackAmount)));
        cardRepository.save(from);

        transaction.setFrom_card_id(from.getLast4());
        transaction.setAmount(onboarding.getAmount());
        transaction.setCashbackPercent(fees.cashbackPercent());
        transaction.setCashbackValue(cashbackAmount);
        transaction.setCurrency(from.getCurrency());
        transaction.setStatus(PaymentStatus.COMPLETED);
        transaction.setPhone_number(onboarding.getPhoneNumber());
        ptRepository.save(transaction);

        Payment payment = Payment.builder()
                .amount(onboarding.getAmount())
                .currency(onboarding.getCurrency())
                .creationDate(Instant.now())
                .fromCardNumber(from.getLast4())
                .phoneNumber(onboarding.getPhoneNumber())
                .build();
        paymentRepository.save(payment);

        onboarding.setPaymentStatus(PaymentStatus.COMPLETED);
        poRepository.save(onboarding);

        return paymentMapper.toPaymentResponse(payment);
    }
}
