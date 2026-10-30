package com.example.paygenix.exceptions;

import org.springframework.http.HttpStatus;

public class InvalidRefreshTokenException extends BusinessException {

    private final String errorCode;

    private InvalidRefreshTokenException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public static InvalidRefreshTokenException invalid() {
        return new InvalidRefreshTokenException("Refresh token is invalid or expired", "INVALID_REFRESH_TOKEN");
    }

    public static InvalidRefreshTokenException reused() {
        return new InvalidRefreshTokenException("Refresh token was already used; the session is revoked", "REFRESH_TOKEN_REUSED");
    }

    @Override
    public String getErrorCode() {
        return errorCode;
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.UNAUTHORIZED;
    }
}
