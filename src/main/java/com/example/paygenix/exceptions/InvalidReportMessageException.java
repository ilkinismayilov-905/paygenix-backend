package com.example.paygenix.exceptions;

import org.springframework.http.HttpStatus;

public class InvalidReportMessageException extends BusinessException {

    public InvalidReportMessageException() {
        super("Report message must not be empty");
    }

    @Override
    public String getErrorCode() {
        return "INVALID_REPORT_MESSAGE";
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.BAD_REQUEST;
    }
}
