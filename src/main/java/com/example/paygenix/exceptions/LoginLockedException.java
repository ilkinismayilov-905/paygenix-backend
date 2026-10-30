package com.example.paygenix.exceptions;

public class LoginLockedException extends BusinessException {

    public LoginLockedException() {
        super("Too many login attempts, try again later");
    }

    @Override
    public String getErrorCode() {
        return "LOGIN_LOCKED";
    }
}
