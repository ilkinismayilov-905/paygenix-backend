package com.example.paygenix.exceptions;

import org.springframework.http.HttpStatus;

public class LoginLockedException extends BusinessException {

    public LoginLockedException() {
        super("Too many login attempts, try again later");
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
