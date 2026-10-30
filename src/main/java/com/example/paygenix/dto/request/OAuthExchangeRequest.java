package com.example.paygenix.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OAuthExchangeRequest(
        @NotBlank @Size(max = 64) String code
) {}
