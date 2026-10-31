package com.example.paygenix.exceptions;

import org.springframework.http.HttpStatus;

import java.time.Duration;

public class PinRejectedException extends BusinessException {

    private final String errorCode;
    private final Duration retryAfter;

    private PinRejectedException(String message, String errorCode, Duration retryAfter) {
        super(message);
        this.errorCode = errorCode;
        this.retryAfter = retryAfter;
    }

    public static PinRejectedException invalid() {
        return new PinRejectedException("Invalid PIN", "INVALID_PIN", null);
    }

    public static PinRejectedException notSet() {
        return new PinRejectedException("Set a PIN before confirming transactions", "PIN_NOT_SET", null);
    }

    public static PinRejectedException locked(Duration retryAfter) {
        return new PinRejectedException("Too many wrong PINs, try again later", "PIN_LOCKED", retryAfter);
    }

    @Override
    public String getErrorCode() {
        return errorCode;
    }

    @Override
    public Duration getRetryAfter() {
        return retryAfter;
    }

    @Override
    public HttpStatus getStatus() {
        return "PIN_LOCKED".equals(errorCode) ? HttpStatus.TOO_MANY_REQUESTS : HttpStatus.FORBIDDEN;
    }
}
