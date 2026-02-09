package com.example.paygenix.dto.response;

import java.time.LocalDate;

public record LegalDocumentResponse(
        String type,
        String version,
        LocalDate effectiveDate,
        String content
) {
}
