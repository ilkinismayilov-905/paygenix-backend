package com.example.paygenix.exceptions;

public class CardHasBalanceException extends BusinessException {
    public CardHasBalanceException() {
        super("Card still holds a balance; move the funds before closing it.");
    }

    @Override
    public String getErrorCode() {
        return "CARD_HAS_BALANCE";
    }
}
