package com.example.paygenix.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ConfirmPaymentRequest(
        @NotBlank(message = "Flow ID is required")
        String flowId,
        @NotBlank(message = "PIN is required")
        @Pattern(regexp = "\\d{6}", message = "PIN must be 6 digits")
        String pin
) {
    // Never print the PIN.
    @Override
    public String toString() {
        return "ConfirmPaymentRequest[flowId=" + flowId + "]";
    }
}
