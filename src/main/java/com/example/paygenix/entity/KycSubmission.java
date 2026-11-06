package com.example.paygenix.entity;

import com.example.paygenix.enums.KycDocumentType;
import com.example.paygenix.enums.KycStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** One identity document a user sent for review (T-070). Reviewed once: APPROVED or REJECTED with a reason. */
@Entity
@Table(name = "kyc_submissions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class KycSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, updatable = false)
    private KycDocumentType documentType;

    @Column(name = "document_key", nullable = false, updatable = false)
    private String documentKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private KycStatus status;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    public KycSubmission(Long userId, KycDocumentType documentType, String documentKey) {
        this.userId = userId;
        this.documentType = documentType;
        this.documentKey = documentKey;
        this.status = KycStatus.PENDING;
        this.submittedAt = Instant.now();
    }

    public void approve(Long reviewerId) {
        review(KycStatus.APPROVED, reviewerId, null);
    }

    public void reject(Long reviewerId, String reason) {
        review(KycStatus.REJECTED, reviewerId, reason);
    }

    private void review(KycStatus result, Long reviewerId, String reason) {
        if (status != KycStatus.PENDING) {
            throw new IllegalStateException("Submission " + id + " was already reviewed");
        }
        status = result;
        reviewedBy = reviewerId;
        reviewedAt = Instant.now();
        rejectionReason = reason;
    }
}
