package com.example.paygenix.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ChangePinRequest(
        @NotBlank @Pattern(regexp = "\\d{6}", message = "PIN must be 6 digits")
        String newPin,
        @NotBlank @Pattern(regexp = "\\d{6}", message = "PIN must be 6 digits")
        String reEnterNewPin
) {
    @Override
    public String toString() {
        return "ChangePinRequest[]";
    }
}
