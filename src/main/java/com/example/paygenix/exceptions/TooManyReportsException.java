package com.example.paygenix.exceptions;

import org.springframework.http.HttpStatus;

public class TooManyReportsException extends BusinessException {

    public TooManyReportsException() {
        super("Too many reports submitted. Please try again later.");
    }

    @Override
    public String getErrorCode() {
        return "TOO_MANY_REPORTS";
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.TOO_MANY_REQUESTS;
    }
}
