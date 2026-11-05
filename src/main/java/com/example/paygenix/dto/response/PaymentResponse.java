package com.example.paygenix.dto.response;

import com.example.paygenix.enums.Currency;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

public record PaymentResponse(
        String phoneNumber,
        String fromCardNumber,
        @JsonFormat(shape = JsonFormat.Shape.STRING) @Schema(type = "string", example = "\"10.00\"", description = "Decimal string (ADR 0001 money rule)")
        BigDecimal amount,
        Currency currency
) {
}
