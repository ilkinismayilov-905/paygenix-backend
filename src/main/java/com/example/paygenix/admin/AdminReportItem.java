package com.example.paygenix.admin;

import com.example.paygenix.enums.ReportCategory;
import com.example.paygenix.enums.ReportStatus;

import java.time.Instant;

public record AdminReportItem(Long id, Long userId, String username, String email, ReportCategory category, String message,
                              String referenceId, ReportStatus status, Instant createdAt) {
}
