package com.example.paygenix.exceptions;

public class InvalidReportMessageException extends BusinessException {

    public InvalidReportMessageException() {
        super("Report message must not be empty");
    }

    @Override
    public String getErrorCode() {
        return "INVALID_REPORT_MESSAGE";
    }
}

