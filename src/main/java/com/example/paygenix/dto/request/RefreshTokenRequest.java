package com.example.paygenix.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;

@Builder
public record RefreshTokenRequest(
        @NotBlank(message = "Refresh token is required")
        @Size(max = 2048, message = "Refresh token is too long")
        String refreshToken
) {
    @Override
    public String toString() {
        return "RefreshTokenRequest[]";
    }
}
