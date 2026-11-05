package com.example.paygenix.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A legal document version, e.g. {"type": "TERMS", "version": "1"}. */
public record LegalDocumentRef(
        @NotBlank @Size(max = 32) String type,
        @NotBlank @Size(max = 64) String version
) {
}
