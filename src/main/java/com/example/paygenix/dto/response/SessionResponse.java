package com.example.paygenix.dto.response;

import java.time.Instant;
import java.util.UUID;

public record SessionResponse(
        UUID id,
        String deviceName,
        Instant createdAt,
        Instant lastUsedAt,
        boolean current
) {
}
