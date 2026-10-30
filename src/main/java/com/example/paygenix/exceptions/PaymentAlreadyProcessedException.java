package com.example.paygenix.exceptions;

import org.springframework.http.HttpStatus;

public class PaymentAlreadyProcessedException extends BusinessException {

    public PaymentAlreadyProcessedException() {
        super("Payment was already processed");
    }

    @Override
    public String getErrorCode() {
        return "PAYMENT_ALREADY_PROCESSED";
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.CONFLICT;
    }
}
