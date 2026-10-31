package com.example.paygenix.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UsernameRequest(
        @NotBlank(message = "Username is required")
        @Pattern(regexp = "[A-Za-z0-9._]{3,30}", message = "Username must be 3-30 letters, digits, dots or underscores")
        String username
) {
}
