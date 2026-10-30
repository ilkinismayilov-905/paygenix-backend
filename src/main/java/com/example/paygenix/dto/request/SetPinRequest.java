package com.example.paygenix.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record SetPinRequest(
        @NotBlank @Pattern(regexp = "\\d{6}", message = "PIN must be 6 digits")
        String pin
) {
    @Override
    public String toString() {
        return "SetPinRequest[]";
    }
}
