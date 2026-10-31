package com.example.paygenix.exceptions;

import org.springframework.http.HttpStatus;

import java.time.Duration;

public abstract class BusinessException extends RuntimeException {
    public BusinessException(String message) {
        super(message);
    }
    protected BusinessException(String message, Throwable cause) {
        super(message, cause);
    }

    public abstract String getErrorCode();

    public abstract HttpStatus getStatus();

    // How long the client should wait before retrying; sent as Retry-After. Null when retrying sooner cannot help.
    public Duration getRetryAfter() {
        return null;
    }
}
