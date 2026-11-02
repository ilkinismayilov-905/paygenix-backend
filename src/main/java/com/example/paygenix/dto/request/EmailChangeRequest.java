package com.example.paygenix.dto.request;

import com.example.paygenix.entity.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record EmailChangeRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank(message = "PIN is required")
        @Pattern(regexp = "\\d{6}", message = "PIN must be 6 digits")
        String pin
) {
    public EmailChangeRequest {
        email = User.normalizeEmail(email);
    }

    // Never print the PIN.
    @Override
    public String toString() {
        return "EmailChangeRequest[]";
    }
}
