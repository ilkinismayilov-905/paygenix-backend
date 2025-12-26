package com.example.paygenix.dto.response;

import com.example.paygenix.enums.TransactionStatus;

import java.math.BigDecimal;

public record TransactionsResponse(
        String transactionId,
        String fromCard,          // **** **** **** 1234
        String toCard,            // **** **** **** 5678
        String amount,            // $100.00
        String commissionRate,    // 5%
        String commissionValue,   // $5.00
        String totalAmount,       // $105.00
        TransactionStatus status,
        String createdAt

) {

}
