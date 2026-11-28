package com.example.paygenix.service;

import com.example.paygenix.dto.request.PaymentRequest;
import com.example.paygenix.dto.response.PaymentResponse;
import com.example.paygenix.entity.Cards;
import com.example.paygenix.entity.PaymentOnboarding;
import com.example.paygenix.entity.User;
import com.example.paygenix.enums.PaymentStatus;
import com.example.paygenix.exceptions.InsufficientBalanceException;
import com.example.paygenix.phone.OperatorResult;
import com.example.paygenix.phone.PhoneTopUpException;
import com.example.paygenix.phone.PhoneTopUpProvider;
import com.example.paygenix.repository.PaymentOnboardingRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    /** A started payment must be confirmed within this time (T-132); after it the flow answers 410 PAYMENT_EXPIRED. */
    public static final Duration FLOW_TTL = Duration.ofMinutes(15);

    static boolean isExpired(PaymentOnboarding flow, Instant now) {
        return flow.getCreationDate() != null && flow.getCreationDate().plus(FLOW_TTL).isBefore(now);
    }

    private final CardService cardService;
    private final PaymentOnboardingRepository poRepository;
    private final PhoneTopUpProvider phoneTopUpProvider;
    private final LegalAcceptanceService legalAcceptanceService;
    private final PaymentSettlement settlement;
    private final MeterRegistry meterRegistry;

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

    /**
     * T-144: reserve (hold the amount, commit) → call the operator with no transaction open → settle or release.
     * Not transactional itself: each step commits on its own, so no row stays locked while the operator answers.
     * An unknown answer leaves the amount held; confirming again repeats the call with the same reference (the
     * operator delivers at most once per reference), and PaymentReconciliation finishes it if nobody does.
     */
    public PaymentResponse confirmPayment(String flowId, String pin, User payer) {
        PaymentSettlement.Reservation reservation = settlement.reserve(flowId, pin, payer);
        OperatorResult result = callOperator(reservation);
        meterRegistry.counter("paygenix.payments.operator", "result", result.name()).increment();
        return switch (result) {
            case DELIVERED -> settlement.settle(reservation.reference());
            case NOT_DELIVERED -> {
                settlement.release(reservation.reference());
                throw PhoneTopUpException.rejected();
            }
            case UNKNOWN -> throw PhoneTopUpException.timeout();
        };
    }

    private OperatorResult callOperator(PaymentSettlement.Reservation r) {
        try {
            return phoneTopUpProvider.topUp(r.reference(), r.phoneNumber(), r.amount(), r.currency());
        } catch (RuntimeException e) {
            // an adapter should answer UNKNOWN itself; an exception means the same: maybe delivered, keep the hold
            log.warn("Operator call for top-up {} failed: {}", r.reference(), e.toString());
            return OperatorResult.UNKNOWN;
        }
    }
}
