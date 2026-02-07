package com.example.paygenix.dto.response;

import com.example.paygenix.enums.CardNetwork;

import java.math.BigDecimal;

public record CardResponse(
        Long id,
        String cardNumber,
        String expirationDate,   // 03/27
        String cardHolderName,
        CardNetwork cardNetwork,
        String displayAmount) {

}
