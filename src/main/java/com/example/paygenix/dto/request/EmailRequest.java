package com.example.paygenix.dto.request;

import com.example.paygenix.entity.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EmailRequest(@NotBlank @Email @Size(max = 255) String email) {
    public EmailRequest {
        email = User.normalizeEmail(email);
    }
}
