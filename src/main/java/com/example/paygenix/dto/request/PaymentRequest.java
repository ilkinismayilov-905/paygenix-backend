package com.example.paygenix.dto.request;

import java.math.BigDecimal;

public record PaymentRequest(
        String phoneNumber,
        String fromCardNumber,
        BigDecimal amount
) {
}
