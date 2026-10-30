package com.example.paygenix.dto.response;

import com.example.paygenix.enums.Currency;

import java.math.BigDecimal;

public record PaymentResponse(
        String phoneNumber,
        String fromCardNumber,
        BigDecimal amount,
        Currency currency
) {
}
