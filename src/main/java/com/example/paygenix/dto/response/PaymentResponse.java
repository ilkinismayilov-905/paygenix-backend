package com.example.paygenix.dto.response;

import java.math.BigDecimal;

public record PaymentResponse(
        String phoneNumber,
        String fromCardNumber,
        BigDecimal amount
) {
}
