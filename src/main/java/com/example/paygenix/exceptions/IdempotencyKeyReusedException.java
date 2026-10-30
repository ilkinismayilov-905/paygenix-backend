package com.example.paygenix.exceptions;

import org.springframework.http.HttpStatus;

public class IdempotencyKeyReusedException extends BusinessException {

    public IdempotencyKeyReusedException() {
        super("Idempotency key was already used for a different request");
    }

    @Override
    public String getErrorCode() {
        return "IDEMPOTENCY_KEY_REUSED";
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.UNPROCESSABLE_CONTENT;
    }
}
