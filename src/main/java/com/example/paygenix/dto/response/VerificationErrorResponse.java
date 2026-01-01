package com.example.paygenix.dto.response;

import com.example.paygenix.enums.VerificationField;

import java.util.List;

public record VerificationErrorResponse(
        String message,
        List<VerificationField> mismatchedFields
) {
}
