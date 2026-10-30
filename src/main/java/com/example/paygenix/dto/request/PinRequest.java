package com.example.paygenix.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// Last onboarding step: the login password and the 6-digit transaction PIN.
public record PinRequest(
        @NotBlank(message = "PIN is required")
        @Pattern(regexp = "\\d{6}", message = "PIN must be 6 digits")
        String pin,
        // bcrypt only uses the first 72 bytes
        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 72, message = "Password must be 8-72 characters")
        String password
) {
    @Override
    public String toString() {
        return "PinRequest[]";
    }
}
