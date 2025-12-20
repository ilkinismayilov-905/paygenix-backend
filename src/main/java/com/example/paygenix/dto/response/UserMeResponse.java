package com.example.paygenix.dto.response;

import java.time.Instant;
import java.util.Set;

public record UserMeResponse(
        String username,
        Set<String> roles,
        Instant expiresAt
) {}

