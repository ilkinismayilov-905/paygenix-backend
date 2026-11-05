package com.example.paygenix.dto.request;

import com.example.paygenix.dto.LegalDocumentRef;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.nio.charset.StandardCharsets;
import java.util.List;

// Last onboarding step: the login password, the 6-digit transaction PIN and the Terms/Privacy versions the user accepted.
public record PinRequest(
        @NotBlank(message = "PIN is required")
        @Pattern(regexp = "\\d{6}", message = "PIN must be 6 digits")
        String pin,
        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 72, message = "Password must be 8-72 characters")
        String password,
        // may be empty only when nothing is published; the service checks that nothing stays pending
        @NotNull(message = "Accepted legal documents are required")
        @Size(max = 10)
        List<@Valid LegalDocumentRef> acceptedLegalDocuments
) {
    // bcrypt refuses to hash more than 72 bytes; non-ASCII characters take 2-4 bytes each
    @JsonIgnore // validation-only; keeps it out of JSON and the OpenAPI schema
    @AssertTrue(message = "Password must be at most 72 bytes")
    public boolean isPasswordWithinBcryptLimit() {
        return password == null || password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }

    @Override
    public String toString() {
        return "PinRequest[]";
    }
}
