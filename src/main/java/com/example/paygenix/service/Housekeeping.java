package com.example.paygenix.service;

import com.example.paygenix.repository.IdempotencyKeyRepository;
import com.example.paygenix.repository.PaymentOnboardingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/** Periodic clean-up. Each job is one idempotent statement, so running on several instances at once is harmless. */
@Service
@RequiredArgsConstructor
@Slf4j
public class Housekeeping {

    static final Duration IDEMPOTENCY_KEY_RETENTION = Duration.ofHours(24);

    private final PaymentOnboardingRepository payments;
    private final IdempotencyKeyRepository idempotencyKeys;

    // T-132: started payments nobody confirmed; confirm already refuses them, this makes the state visible
    @Scheduled(fixedDelay = 5, initialDelay = 1, timeUnit = java.util.concurrent.TimeUnit.MINUTES)
    @Transactional
    public int expireStalePayments() {
        int expired = payments.expirePendingBefore(Instant.now().minus(PaymentService.FLOW_TTL));
        if (expired > 0) {
            log.info("Expired {} unconfirmed payments", expired);
        }
        return expired;
    }

    @Scheduled(cron = "0 45 3 * * *", zone = "UTC")
    @Transactional
    public int purgeIdempotencyKeys() {
        int purged = idempotencyKeys.deleteCreatedBefore(Instant.now().minus(IDEMPOTENCY_KEY_RETENTION));
        if (purged > 0) {
            log.info("Purged {} idempotency keys", purged);
        }
        return purged;
    }
}
