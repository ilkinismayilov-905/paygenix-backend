package com.example.paygenix.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

// otp: code sent to the new address; currentEmailOtp: code sent to the address the account has now.
public record EmailChangeConfirmRequest(
        @NotBlank @Pattern(regexp = "\\d{6}", message = "Code must be 6 digits") String otp,
        @NotBlank @Pattern(regexp = "\\d{6}", message = "Code must be 6 digits") String currentEmailOtp
) {
    @Override
    public String toString() {
        return "EmailChangeConfirmRequest[]";
    }
}
