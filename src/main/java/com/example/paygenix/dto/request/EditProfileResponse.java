package com.example.paygenix.dto.request;

import com.example.paygenix.enums.Gender;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;

public record EditProfileResponse(
        String fullName,
        String email,
        String phoneNumber,

        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd-MM-yyyy")
        LocalDate dateOfBirth,
        Gender gender
) {
}
