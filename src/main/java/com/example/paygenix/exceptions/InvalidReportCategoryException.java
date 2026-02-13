package com.example.paygenix.exceptions;

public class InvalidReportCategoryException extends BusinessException {

    public InvalidReportCategoryException() {
        super("Invalid or missing report category");
    }

    @Override
    public String getErrorCode() {
        return "INVALID_REPORT_CATEGORY";
    }
}
