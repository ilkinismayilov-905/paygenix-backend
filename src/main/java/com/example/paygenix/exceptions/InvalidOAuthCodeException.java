package com.example.paygenix.exceptions;

public class InvalidOAuthCodeException extends BusinessException {

    public InvalidOAuthCodeException() {
        super("Sign-in code is invalid, expired or already used");
    }

    @Override
    public String getErrorCode() {
        return "INVALID_OAUTH_CODE";
    }
}
