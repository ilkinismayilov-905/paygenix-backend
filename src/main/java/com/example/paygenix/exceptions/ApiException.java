package com.example.paygenix.exceptions;

import org.springframework.http.HttpStatus;

// A client error with its status and stable code, for cases that do not need their own exception class.
public class ApiException extends BusinessException {

    private final HttpStatus status;
    private final String errorCode;

    public ApiException(HttpStatus status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public static ApiException validation(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message);
    }

    public static ApiException otpExpired() {
        return new ApiException(HttpStatus.BAD_REQUEST, "OTP_EXPIRED", "The code has expired; request a new one");
    }

    public static ApiException invalidOtp() {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_OTP", "The code is not correct");
    }

    public static ApiException tooManyAttempts() {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_ATTEMPTS", "Too many wrong codes; start again");
    }

    public static ApiException otpSendTooFrequent() {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "OTP_SEND_TOO_FREQUENT", "Wait a minute before requesting another code");
    }

    public static ApiException flowNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "FLOW_NOT_FOUND", "Flow not found");
    }

    public static ApiException invalidStep() {
        return new ApiException(HttpStatus.CONFLICT, "INVALID_STEP", "This step is not allowed now");
    }

    public static ApiException flowExpired() {
        return new ApiException(HttpStatus.GONE, "FLOW_EXPIRED", "The flow has expired; start again");
    }

    @Override
    public String getErrorCode() {
        return errorCode;
    }

    @Override
    public HttpStatus getStatus() {
        return status;
    }
}
