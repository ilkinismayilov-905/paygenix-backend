package com.example.paygenix.dto.request;

public record ChangePinRequest(
        String newPin,
        String reEnterNewPin
) {
}
