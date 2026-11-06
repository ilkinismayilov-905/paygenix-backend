package com.example.paygenix.topup;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

// amount as a decimal string ("25.00"), like every money amount in the API
public record TopUpRequest(
        @NotNull Long cardId,
        @NotNull @DecimalMin("1.00") @DecimalMax("10000.00") @Digits(integer = 5, fraction = 2)
        @Schema(type = "string", example = "\"25.00\"", pattern = "^\\d{1,5}(\\.\\d{1,2})?$", description = "Decimal string; a JSON number is also accepted")
        BigDecimal amount
) {
}
