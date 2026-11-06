package com.example.paygenix.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Shown to the user, so it says what to fix ("The photo is blurred"), not internal notes.
public record KycRejectRequest(@NotBlank @Size(max = 500) String reason) {
}
