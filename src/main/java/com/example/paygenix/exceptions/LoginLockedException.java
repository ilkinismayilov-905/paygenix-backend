package com.example.paygenix.exceptions;

import org.springframework.http.HttpStatus;

import java.time.Duration;

public class LoginLockedException extends BusinessException {

    private final Duration retryAfter;

    public LoginLockedException(Duration retryAfter) {
        super("Too many login attempts, try again later");
        this.retryAfter = retryAfter;
    }

    @Override
    public Duration getRetryAfter() {
        return retryAfter;
    }

    @Override
    public String getErrorCode() {
        return "LOGIN_LOCKED";
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.TOO_MANY_REQUESTS;
    }
}
