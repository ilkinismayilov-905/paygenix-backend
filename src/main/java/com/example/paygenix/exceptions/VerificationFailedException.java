package com.example.paygenix.exceptions;

import com.example.paygenix.enums.VerificationField;

import java.util.List;

public class VerificationFailedException extends RuntimeException {
    private final List<VerificationField> mismatchedFields;

    public VerificationFailedException(List<VerificationField> mismatchedFields) {
        super("Verification failed");
        this.mismatchedFields = mismatchedFields;
    }

    public List<VerificationField> getMismatchedFields() {
        return mismatchedFields;
    }
}
