package com.example.paygenix.dto.request;

import jakarta.validation.constraints.NotBlank;

public record OAuthExchangeRequest(
        @NotBlank String uuid
) {}
