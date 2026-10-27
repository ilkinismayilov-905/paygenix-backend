package com.example.paygenix.dto.response;

import com.example.paygenix.enums.CardNetwork;
import com.example.paygenix.enums.Currency;

public record CardResponse(
        Long id,
        String maskedNumber,     // **** **** **** 1234
        String expirationDate,   // 03/27
        String cardHolderName,
        CardNetwork cardNetwork,
        Currency currency,
        String displayAmount) {
}
