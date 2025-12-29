package com.example.paygenix.dto.request;

public record ConfirmPaymentRequest(
        String flowId,
        String pin
) {
}
