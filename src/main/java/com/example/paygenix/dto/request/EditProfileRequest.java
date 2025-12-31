package com.example.paygenix.dto.request;

import com.example.paygenix.enums.Gender;
import com.example.paygenix.validation.Create;
import com.example.paygenix.validation.Update;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import javax.annotation.RegEx;
import java.time.LocalDate;

public record EditProfileRequest(
        @NotNull(groups = Create.class)
        String fullName,
        @NotNull(groups = Create.class)
        String email,
        @NotNull(groups = Create.class)
        @Pattern(
                regexp = "^\\+994\\d{9}$",
                message = "Phone number must start with +994 and contain 9 digits after it"
        )
        String phoneNumber,
        @NotNull(groups = Create.class)
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd-MM-yyyy")
        LocalDate dateOfBirth,
        @NotNull(groups = Create.class)
        Gender gender
) {

}
