package com.example.paygenix.dto.response;

import com.example.paygenix.enums.CardNetwork;
import com.example.paygenix.enums.Currency;
import io.swagger.v3.oas.annotations.media.Schema;

public record CardResponse(
        Long id,
        String maskedNumber,     // **** **** **** 1234
        String expirationDate,   // 03/27
        String cardHolderName,
        CardNetwork cardNetwork,
        Currency currency,
        @Schema(example = "1300.00", description = "Balance as a decimal string, two decimals") String amount,
        String displayAmount) {     // $1,300.00, server-formatted
}
