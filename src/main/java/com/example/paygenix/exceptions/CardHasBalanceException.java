package com.example.paygenix.exceptions;

import org.springframework.http.HttpStatus;

public class CardHasBalanceException extends BusinessException {
    public CardHasBalanceException() {
        super("Card still holds a balance; move the funds before closing it.");
    }

    @Override
    public String getErrorCode() {
        return "CARD_HAS_BALANCE";
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.CONFLICT;
    }
}
