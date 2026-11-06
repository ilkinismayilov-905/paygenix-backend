package com.example.paygenix.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

public record TransferRequest(
        @NotNull(message = "Source card is required") @Positive
        @Schema(description = "Id of one of the caller's cards (GET /api/v1/cards/me)")
        Long fromCardId,
        @NotBlank @Pattern(regexp = "\\d{16}", message = "Card number must be 16 digits")
        String toCardNumber,
        @NotNull @DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
        @Digits(integer = 15, fraction = 2, message = "Amount can have at most 2 decimal places")
        @Schema(type = "string", example = "\"10.00\"", pattern = "^\\d{1,15}(\\.\\d{1,2})?$", description = "Decimal string; a JSON number is also accepted")
        BigDecimal amount,
        @NotBlank(message = "PIN is required")
        @Pattern(regexp = "\\d{6}", message = "PIN must be 6 digits")
        String pin
) {
    // Never print card numbers or the PIN.
    @Override
    public String toString() {
        return "TransferRequest[amount=" + amount + "]";
    }
}
