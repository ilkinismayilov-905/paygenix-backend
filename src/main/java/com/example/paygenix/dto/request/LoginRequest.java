package com.example.paygenix.dto.request;

import com.example.paygenix.entity.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @Email
        @NotBlank(message = "Email cannot be blank")
        @Size(max = 255)
        String email,
        // matches the sign-up limit; also keeps oversized input away from bcrypt
        @NotBlank(message = "Password cannot be blank")
        @Size(max = 72, message = "Password must be at most 72 characters")
        String password
) {
    public LoginRequest {
        email = User.normalizeEmail(email);
    }

    @Override
    public String toString() {
        return "LoginRequest[email=" + email + "]";
    }
}
