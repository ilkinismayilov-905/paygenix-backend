package com.example.paygenix.dto.response;

import com.example.paygenix.enums.TransactionStatus;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record TransactionsResponse(
        String transactionId,
        String fromCard,          // **** **** **** 1234
        String toCard,            // **** **** **** 5678
        String amount,            // $100.00
        String commissionRate,    // 5%
        String commissionValue,   // $5.00
        String totalAmount,       // $105.00
        TransactionStatus status,
        @JsonFormat(
                shape = JsonFormat.Shape.STRING,
                pattern = "dd-MM-yyyy HH:mm:ss",
                timezone = "Asia/Baku"
        )
        OffsetDateTime createdAt

) {

}
