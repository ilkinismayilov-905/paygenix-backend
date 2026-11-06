package com.example.paygenix.topup;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TopUpRepository extends JpaRepository<TopUp, Long> {

    Optional<TopUp> findByUserIdAndIdemKey(Long userId, String idemKey);

    Optional<TopUp> findByStripeSessionIdAndUserId(String stripeSessionId, Long userId);

    // the webhook holds the row, so a redelivered event cannot credit twice
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM TopUp t WHERE t.stripeSessionId = :sessionId")
    Optional<TopUp> findBySessionIdForUpdate(@Param("sessionId") String sessionId);
}
