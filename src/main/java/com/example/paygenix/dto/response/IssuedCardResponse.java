package com.example.paygenix.dto.response;

// Returned once, when the card is issued: the only time the full number leaves the server.
public record IssuedCardResponse(
        String cardNumber,
        CardResponse card) {
}
