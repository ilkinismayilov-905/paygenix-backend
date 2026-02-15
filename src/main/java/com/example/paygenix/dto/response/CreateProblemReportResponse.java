package com.example.paygenix.dto.response;

import java.time.Instant;

public record CreateProblemReportResponse(
        Long id,
        String status,
        Instant createdAt
) {}
