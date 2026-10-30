package com.example.paygenix.exceptions;

import org.springframework.http.HttpStatus;

public class InvalidReportCategoryException extends BusinessException {

    public InvalidReportCategoryException() {
        super("Invalid or missing report category");
    }

    @Override
    public String getErrorCode() {
        return "INVALID_REPORT_CATEGORY";
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.BAD_REQUEST;
    }
}
