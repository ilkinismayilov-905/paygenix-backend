package com.example.paygenix.exceptions;

public class TooManyReportsException extends BusinessException {

    public TooManyReportsException() {
        super("Too many reports submitted. Please try again later.");
    }

    @Override
    public String getErrorCode() {
        return "TOO_MANY_REPORTS";
    }
}

