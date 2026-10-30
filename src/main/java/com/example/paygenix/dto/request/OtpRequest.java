package com.example.paygenix.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record OtpRequest(@NotBlank @Pattern(regexp = "\\d{6}", message = "Code must be 6 digits") String otp) {
}
