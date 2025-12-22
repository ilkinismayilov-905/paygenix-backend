package com.example.paygenix.dto.response;

import java.time.Instant;
import java.util.Set;

public record UserMeResponse(
        Long id,
        String username,
        Set<String> roles,
        long expiresAt
) {}

