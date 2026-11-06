package com.example.paygenix.repository;

import com.example.paygenix.entity.KycSubmission;
import com.example.paygenix.enums.KycStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface KycSubmissionRepository extends JpaRepository<KycSubmission, Long> {

    Optional<KycSubmission> findFirstByUserIdOrderBySubmittedAtDescIdDesc(Long userId);

    boolean existsByUserIdAndStatus(Long userId, KycStatus status);

    Slice<KycSubmission> findByStatus(KycStatus status, Pageable pageable);

    // a review holds the row, so two admins cannot decide the same submission at once
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT k FROM KycSubmission k WHERE k.id = :id")
    Optional<KycSubmission> findByIdForUpdate(@Param("id") Long id);
}
