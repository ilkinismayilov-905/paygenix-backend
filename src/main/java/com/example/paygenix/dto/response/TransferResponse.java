package com.example.paygenix.dto.response;

import java.math.BigDecimal;

public record TransferResponse(
        String fromCardNumber,
        String toCardNumber,
        BigDecimal amount
) {
}
