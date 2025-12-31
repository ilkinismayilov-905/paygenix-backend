package com.example.paygenix.dto.request;

import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

public record PaymentRequest(
        @Pattern(
                regexp = "^\\+994\\d{9}$",
                message = "Phone number must start with +994 and contain 9 digits after it"
        )
        String phoneNumber,

        @Pattern(
                regexp = "^\\d{16}$",
                message = "Card number must contain exactly 16 digits"
        )
        String fromCardNumber,
        BigDecimal amount
) {
}
