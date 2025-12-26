package com.example.paygenix.dto.request;

import java.math.BigDecimal;

public record TransferRequest(
        String fromCardNumber,
        String toCardNumber,
        BigDecimal amount
) {
}
