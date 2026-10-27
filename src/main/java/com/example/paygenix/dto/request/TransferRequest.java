package com.example.paygenix.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

public record TransferRequest(
        @NotBlank @Pattern(regexp = "\\d{16}", message = "Card number must be 16 digits")
        String fromCardNumber,
        @NotBlank @Pattern(regexp = "\\d{16}", message = "Card number must be 16 digits")
        String toCardNumber,
        @NotNull @DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
        @Digits(integer = 15, fraction = 2, message = "Amount can have at most 2 decimal places")
        BigDecimal amount,
        @NotBlank(message = "PIN is required")
        String pin
) {
    // Never print card numbers or the PIN.
    @Override
    public String toString() {
        return "TransferRequest[amount=" + amount + "]";
    }
}
