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
        ReportStatus status,
        Instant createdAt
) {
}
