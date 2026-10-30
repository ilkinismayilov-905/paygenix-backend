package com.example.paygenix.exceptions;

import org.springframework.http.HttpStatus;

public class PinAlreadySetException extends BusinessException {

    public PinAlreadySetException() {
        super("PIN is already set; use the change-PIN flow");
    }

    @Override
    public String getErrorCode() {
        return "PIN_ALREADY_SET";
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.CONFLICT;
    }
}
