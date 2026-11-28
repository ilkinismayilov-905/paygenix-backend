package com.example.paygenix.exceptions;

import org.springframework.http.HttpStatus;

import java.time.Duration;

/** Too many login attempts from one client address (T-142); the per-email limit is {@link LoginLockedException}. */
public class LoginRateLimitedException extends BusinessException {

    private final Duration retryAfter;

    public LoginRateLimitedException(Duration retryAfter) {
        super("Too many login attempts, try again later");
        this.retryAfter = retryAfter;
    }

    @Override
    public Duration getRetryAfter() {
        return retryAfter;
    }

    @Override
    public String getErrorCode() {
        return "LOGIN_RATE_LIMITED";
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.TOO_MANY_REQUESTS;
    }
}
