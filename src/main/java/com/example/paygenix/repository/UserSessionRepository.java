package com.example.paygenix.repository;

import com.example.paygenix.entity.UserSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {

    // Serializes refreshes of one session: of two parallel refreshes with the same token, the second sees reuse.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM UserSession s WHERE s.id = :id")
    Optional<UserSession> findByIdForUpdate(UUID id);

    boolean existsByIdAndUserIdAndRevokedAtIsNullAndExpiresAtAfter(UUID id, Long userId, Instant now);

    List<UserSession> findByUserIdAndRevokedAtIsNullAndExpiresAtAfterOrderByLastUsedAtDesc(Long userId, Instant now);

    Optional<UserSession> findByIdAndUserIdAndRevokedAtIsNull(UUID id, Long userId);

    @Modifying
    @Query("UPDATE UserSession s SET s.revokedAt = :now WHERE s.userId = :userId AND s.revokedAt IS NULL")
    int revokeAll(Long userId, Instant now);
}
