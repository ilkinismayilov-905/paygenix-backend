package com.example.paygenix.dto.request;

import com.example.paygenix.dto.LegalDocumentRef;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/** The document versions the user was shown and accepted. */
public record LegalAcceptanceRequest(
        @NotEmpty(message = "Accepted documents are required") @Size(max = 10) List<@Valid LegalDocumentRef> documents
) {
}
