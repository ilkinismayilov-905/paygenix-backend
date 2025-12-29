package com.example.paygenix.dto.response;

import com.example.paygenix.enums.PaymentStatus;
import com.example.paygenix.enums.TransactionStatus;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.OffsetDateTime;

public record PaymentTransactionResponse(
        String transactionId,
        String fromCard,          // **** **** **** 1234
        String number,            // **** **** **** 5678
        String amount,            // $100.00
        String cashbackPercent,    // 5%
        String cashbackValue,   // $5.00
        PaymentStatus status,
        @JsonFormat(
                shape = JsonFormat.Shape.STRING,
                pattern = "dd-MM-yyyy HH:mm:ss",
                timezone = "Asia/Baku"
        )
        OffsetDateTime createdAt
) {
}
