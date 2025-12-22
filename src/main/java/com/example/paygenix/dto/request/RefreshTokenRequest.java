package com.example.paygenix.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

@Builder
public record RefreshTokenRequest(
        @NotBlank(message = "Refresh token boş ola bilməz")
        String refreshToken
) {
}
