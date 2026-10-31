package com.example.paygenix.dto.request;

import com.example.paygenix.enums.Gender;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

// Partial update: a missing field keeps its value. The first save must carry all of them (EditProfileService).
public record EditProfileRequest(
        @Pattern(regexp = ".*\\S.*", message = "Full name must not be blank")
        @Size(max = 100, message = "Full name must be at most 100 characters")
        String fullName,
        @Pattern(
                regexp = "^\\+994\\d{9}$",
                message = "Phone number must start with +994 and contain 9 digits after it"
        )
        String phoneNumber,
        @Past(message = "Date of birth must be in the past")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd-MM-yyyy")
        LocalDate dateOfBirth,
        Gender gender
) {
}
