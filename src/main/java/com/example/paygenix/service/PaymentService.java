package com.example.paygenix.service;

import com.example.paygenix.dto.request.PaymentRequest;
import com.example.paygenix.dto.response.PaymentResponse;
import com.example.paygenix.entity.Cards;
import com.example.paygenix.entity.Payment;
import com.example.paygenix.entity.PaymentOnboarding;
import com.example.paygenix.entity.PaymentTransaction;
import com.example.paygenix.entity.User;
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
import java.math.RoundingMode;
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

    @Transactional
    public String transfer(PaymentRequest req, User payer) {
        Cards from = cardService.findOwnedByNumber(payer, req.fromCardNumber());

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

        BigDecimal cashbackPercent = transaction.getCashbackPercent();
        BigDecimal cashbackAmount = onboarding.getAmount()
                .multiply(cashbackPercent)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.DOWN);

        transaction.setTransactionId(ptService.generatePaymentTransactionId());
        ledgerService.post("PAYMENT", transaction.getTransactionId(), from.getCurrency(), List.of(
                Leg.card(from, onboarding.getAmount().negate()),
                Leg.system(SystemAccount.PAYMENT_PAYOUT, onboarding.getAmount())));
        // shortcut: cashback is still the entity default 0.5%, not config (T-061)
        ledgerService.post("CASHBACK", transaction.getTransactionId(), from.getCurrency(), List.of(
                Leg.system(SystemAccount.CASHBACK_EXPENSE, cashbackAmount.negate()),
                Leg.card(from, cashbackAmount)));
        cardRepository.save(from);

        transaction.setFrom_card_id(from.getLast4());
        transaction.setAmount(onboarding.getAmount());
        transaction.setCashbackPercent(cashbackPercent);
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
