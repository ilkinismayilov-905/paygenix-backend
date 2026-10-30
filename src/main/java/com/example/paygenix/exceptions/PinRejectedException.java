package com.example.paygenix.exceptions;

import org.springframework.http.HttpStatus;

public class PinRejectedException extends BusinessException {

    private final String errorCode;

    private PinRejectedException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public static PinRejectedException invalid() {
        return new PinRejectedException("Invalid PIN", "INVALID_PIN");
    }

    public static PinRejectedException notSet() {
        return new PinRejectedException("Set a PIN before confirming transactions", "PIN_NOT_SET");
    }

    public static PinRejectedException locked() {
        return new PinRejectedException("Too many wrong PINs, try again later", "PIN_LOCKED");
    }

    @Override
    public String getErrorCode() {
        return errorCode;
    }

    @Override
    public HttpStatus getStatus() {
        return "PIN_LOCKED".equals(errorCode) ? HttpStatus.TOO_MANY_REQUESTS : HttpStatus.FORBIDDEN;
    }
}
