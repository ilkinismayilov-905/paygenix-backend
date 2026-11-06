package com.example.paygenix.dto.response;

import com.example.paygenix.enums.KycDocumentType;
import com.example.paygenix.enums.KycStatus;

import java.time.Instant;

/** A submission as reviewers see it. documentUrl is set only on the single-submission read and works for 5 minutes. */
public record KycReviewItem(Long id, Long userId, String username, String fullName, String email,
                            KycDocumentType documentType, KycStatus status, Instant submittedAt, Instant reviewedAt,
                            String rejectionReason, String documentUrl) {
}
