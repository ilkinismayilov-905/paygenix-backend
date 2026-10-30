package com.example.paygenix.exceptions;

public class PinRejectedException extends BusinessException {

    private final String errorCode;

    private PinRejectedException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public static PinRejectedException invalid() {
        return new PinRejectedException("Invalid PIN", "INVALID_PIN");
    }

    public static PinRejectedException locked() {
        return new PinRejectedException("Too many wrong PINs, try again later", "PIN_LOCKED");
    }

    @Override
    public String getErrorCode() {
        return errorCode;
    }
}
