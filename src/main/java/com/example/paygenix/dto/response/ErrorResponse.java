package com.example.paygenix.dto.response;

public record ErrorResponse(
        boolean success,
        String message
) {}
