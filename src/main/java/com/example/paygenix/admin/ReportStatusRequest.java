package com.example.paygenix.admin;

import com.example.paygenix.enums.ReportStatus;
import jakarta.validation.constraints.NotNull;

public record ReportStatusRequest(@NotNull ReportStatus status) {
}
