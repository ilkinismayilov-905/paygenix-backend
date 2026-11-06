package com.example.paygenix.dto.response;

import com.example.paygenix.enums.Gender;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;

public record EditProfileResponse(
        String fullName,
        String email,
        String phoneNumber,

        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd-MM-yyyy")
        LocalDate dateOfBirth,
        Gender gender,
        String objectName,
        // presigned link to the photo (10 minutes); only on the owner's own reads
        String photoUrl
) {
    public EditProfileResponse withPhotoUrl(String url) {
        return new EditProfileResponse(fullName, email, phoneNumber, dateOfBirth, gender, objectName, url);
    }
}
