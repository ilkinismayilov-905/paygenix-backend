package com.example.paygenix.dto.response;

import com.example.paygenix.enums.Role;

import java.time.Instant;
import java.util.Set;

public record UserMeResponse(
        Long id,
        String username,
        Set<Role> roles,
        long expiresAt
) {}

