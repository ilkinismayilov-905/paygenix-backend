package com.example.paygenix.dto.response;

public record SuccessResponse<T>(
        boolean success,
        T data
) {
    public static <T> SuccessResponse<T> of(T data) {
        return new SuccessResponse<>(true, data);
    }

    public static SuccessResponse<Void> empty() {
        return new SuccessResponse<>(true, null);
    }
}
