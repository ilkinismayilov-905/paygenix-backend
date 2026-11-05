package com.example.paygenix.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

public record PaymentRequest(
        @NotBlank
        @Pattern(
                regexp = "^\\+994\\d{9}$",
                message = "Phone number must start with +994 and contain 9 digits after it"
        )
        String phoneNumber,

        @NotNull(message = "Source card is required") @Positive
        @Schema(description = "Id of one of the caller's cards (GET /api/cards/me)")
        Long fromCardId,
        @NotNull @DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
        @Digits(integer = 15, fraction = 2, message = "Amount can have at most 2 decimal places")
        @Schema(type = "string", example = "\"10.00\"", pattern = "^\\d{1,15}(\\.\\d{1,2})?$", description = "Decimal string; a JSON number is also accepted")
        BigDecimal amount
) {
    @Override
    public String toString() {
        return "PaymentRequest[amount=" + amount + "]";
    }
}
