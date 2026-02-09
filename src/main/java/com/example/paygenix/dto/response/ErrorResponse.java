package com.example.paygenix.dto.response;

import java.time.OffsetDateTime;

public record ErrorResponse(
        OffsetDateTime timestamp,
        int status,
        String message,
        String code
) {}
