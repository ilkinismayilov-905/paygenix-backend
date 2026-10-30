package com.example.paygenix.exceptions;

public class IdempotencyKeyReusedException extends BusinessException {

    public IdempotencyKeyReusedException() {
        super("Idempotency key was already used for a different request");
    }

    @Override
    public String getErrorCode() {
        return "IDEMPOTENCY_KEY_REUSED";
    }
}
