package com.example.paygenix.exceptions;

public class PaymentAlreadyProcessedException extends BusinessException {

    public PaymentAlreadyProcessedException() {
        super("Payment was already processed");
    }

    @Override
    public String getErrorCode() {
        return "PAYMENT_ALREADY_PROCESSED";
    }
}
