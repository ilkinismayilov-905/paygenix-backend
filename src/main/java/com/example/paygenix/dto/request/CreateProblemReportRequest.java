package com.example.paygenix.dto.request;

import com.example.paygenix.enums.ReportCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateProblemReportRequest(
        @NotNull ReportCategory category,
        @NotBlank @Size(max = 2000) String message,
        @Size(max = 100) String referenceId
) {}
