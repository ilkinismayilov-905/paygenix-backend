package com.example.paygenix.dto.response;

import com.example.paygenix.enums.KycDocumentType;

import java.time.Instant;

/**
 * The caller's verification: NOT_SUBMITTED, PENDING (waiting for review), APPROVED (verified) or REJECTED
 * (rejectionReason says why; a new document may be sent). The fields describe the latest submission.
 */
public record KycStatusResponse(String status, KycDocumentType documentType, Instant submittedAt, Instant reviewedAt,
                                String rejectionReason) {
}
