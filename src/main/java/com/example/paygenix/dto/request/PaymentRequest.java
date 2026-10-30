package com.example.paygenix.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

public record PaymentRequest(
        @NotBlank
        @Pattern(
                regexp = "^\\+994\\d{9}$",
                message = "Phone number must start with +994 and contain 9 digits after it"
        )
        String phoneNumber,

        @NotBlank
        @Pattern(
                regexp = "^\\d{16}$",
                message = "Card number must contain exactly 16 digits"
        )
        String fromCardNumber,
        @NotNull @DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
        @Digits(integer = 15, fraction = 2, message = "Amount can have at most 2 decimal places")
        BigDecimal amount
) {
    // Never print the card number.
    @Override
    public String toString() {
        return "PaymentRequest[amount=" + amount + "]";
    }
}
