package com.example.paygenix.dto;

import org.springframework.http.HttpStatus;

import java.time.Instant;
public record ApiResponse<T>(
        Instant timestamp,
        int status,
        String message,
        T data) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(Instant.now(), HttpStatus.OK.value(), "Success", data);
    }
    public static <T> ApiResponse<T> build(HttpStatus status, String message, T data) {
        return new ApiResponse<>(Instant.now(), status.value(), message, data);
    }
}
