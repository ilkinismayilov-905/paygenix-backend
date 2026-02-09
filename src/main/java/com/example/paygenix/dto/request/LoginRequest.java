package com.example.paygenix.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record LoginRequest(
        @Email
        @NotBlank(message = "Email cannot be blank")
        String email,
        @NotBlank(message = "Password cannot be blank")
        String password,
        @Pattern(regexp = "^\\+?[0-9]{10,15}$",
                 message = "Invalid phone number")
        String phoneNumber
)
{
}
