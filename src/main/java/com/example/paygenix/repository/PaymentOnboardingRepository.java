package com.example.paygenix.repository;

import com.example.paygenix.entity.PaymentOnboarding;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentOnboardingRepository extends JpaRepository<PaymentOnboarding, String> {

    // Locks the flow row so two confirms of one flow run one after another and the second sees COMPLETED.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PaymentOnboarding> findByIdAndUserId(String id, Long userId);

    @Modifying
    @Query("UPDATE PaymentOnboarding p SET p.paymentStatus = com.example.paygenix.enums.PaymentStatus.EXPIRED "
            + "WHERE p.paymentStatus = com.example.paygenix.enums.PaymentStatus.PENDING AND p.creationDate < :before")
    int expirePendingBefore(@Param("before") java.time.Instant before);
}
