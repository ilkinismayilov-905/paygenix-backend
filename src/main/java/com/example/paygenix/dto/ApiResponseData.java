package com.example.paygenix.dto;

import org.springframework.http.HttpStatus;

import java.time.Instant;
public record ApiResponseData<T>(
        Instant timestamp,
        int status,
        String message,
        T data) {

    public static <T> ApiResponseData<T> success(T data) {
        return new ApiResponseData<>(Instant.now(), HttpStatus.OK.value(), "Success", data);
    }
    public static <T> ApiResponseData<T> build(HttpStatus status, String message, T data) {
        return new ApiResponseData<>(Instant.now(), status.value(), message, data);
    }
}
