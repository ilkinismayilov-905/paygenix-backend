package com.example.paygenix.dto.request;

import com.example.paygenix.enums.Gender;
import com.example.paygenix.validation.Create;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record VerifyRequest(
        @NotNull(groups = Create.class)
        String fullName,
        @NotNull(groups = Create.class)
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd-MM-yyyy")
        LocalDate dateOfBirth,
        @NotNull(groups = Create.class)
        Gender gender
) {
}
