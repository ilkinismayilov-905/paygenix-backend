package com.example.paygenix.dto.response;

import com.example.paygenix.dto.LegalDocumentRef;
import com.example.paygenix.enums.Role;

import java.util.List;
import java.util.Set;

public record UserMeResponse(
        Long id,
        String username,
        Set<Role> roles,
        long expiresAt,
        // T-121: not empty → show these documents for acceptance; money moves are blocked meanwhile
        List<LegalDocumentRef> pendingLegalDocuments
) {}

