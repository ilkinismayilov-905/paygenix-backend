package com.example.paygenix.dto.response;

import com.example.paygenix.enums.ReportCategory;
import com.example.paygenix.enums.ReportStatus;
import lombok.Builder;
import lombok.NoArgsConstructor;

import java.time.Instant;

public record ProblemReportResponse(
        Long id,
        Long userId,
        ReportCategory category,
        String message,
        String referenceId,
        ReportStatus status,
        Instant createdAt
) {
}
