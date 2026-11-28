package com.example.paygenix.service;

import com.example.paygenix.entity.PaymentOnboarding;
import com.example.paygenix.exceptions.PaymentAlreadyProcessedException;
import com.example.paygenix.phone.OperatorResult;
import com.example.paygenix.repository.PaymentOnboardingRepository;
import com.example.paygenix.phone.PhoneTopUpProvider;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Finishes phone payments whose operator answer was lost (T-144): every minute, held payments older than
 * {@link #RECONCILE_AFTER} are looked up at the operator and settled (delivered) or released (not delivered). One
 * still unknown after {@link #REVIEW_AFTER} is logged as an error each run until a person looks at it; the money
 * stays held meanwhile, which is the safe side.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentReconciliation {

    /** Longer than any operator call may take (PhoneTopUpProvider contract), so an answer in flight is not overtaken. */
    public static final Duration RECONCILE_AFTER = Duration.ofMinutes(2);
    static final Duration REVIEW_AFTER = Duration.ofHours(24);
    private static final int BATCH = 100;

    private final PaymentOnboardingRepository flows;
    private final PhoneTopUpProvider operator;
    private final PaymentSettlement settlement;
    private final MeterRegistry meterRegistry;

    @Scheduled(fixedDelay = 1, initialDelay = 1, timeUnit = TimeUnit.MINUTES)
    public int reconcile() {
        Instant now = Instant.now();
        List<PaymentOnboarding> held = flows.processingReservedBefore(now.minus(RECONCILE_AFTER), PageRequest.of(0, BATCH));
        int finished = 0;
        for (PaymentOnboarding flow : held) {
            String reference = flow.getOperatorReference();
            try {
                OperatorResult result = operator.status(reference);
                meterRegistry.counter("paygenix.payments.reconciled", "result", result.name()).increment();
                switch (result) {
                    case DELIVERED -> {
                        settlement.settle(reference);
                        finished++;
                    }
                    case NOT_DELIVERED -> {
                        if (settlement.release(reference)) finished++;
                    }
                    case UNKNOWN -> {
                        if (flow.getReservedAt().isBefore(now.minus(REVIEW_AFTER))) {
                            log.error("Phone payment {} has been held for over {} with no operator answer; needs manual review",
                                    reference, REVIEW_AFTER);
                        }
                    }
                }
            } catch (PaymentAlreadyProcessedException e) {
                // a retry settled it between the query and now
            } catch (RuntimeException e) {
                // the next run tries again; one bad record must not stop the rest
                log.warn("Could not reconcile phone payment {}: {}", reference, e.toString());
            }
        }
        if (finished > 0) {
            log.info("Reconciled {} held phone payments", finished);
        }
        return finished;
    }
}
