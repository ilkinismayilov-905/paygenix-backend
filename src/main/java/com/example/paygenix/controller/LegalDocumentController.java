package com.example.paygenix.controller;

import com.example.paygenix.dto.response.LegalDocumentResponse;
import com.example.paygenix.entity.LegalDocument;
import com.example.paygenix.service.LegalDocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/legal")
@RequiredArgsConstructor
public class LegalDocumentController {

    private final LegalDocumentService service;

    @GetMapping("/terms")
    public LegalDocumentResponse getDocumentTerms() {
        var doc = service.getActiveDocument("TERMS");
        return new LegalDocumentResponse(
                doc.getType(),
                doc.getVersion(),
                doc.getEffectiveDate(),
                doc.getContent()
        );
    }
    @GetMapping("/privacy")
    public LegalDocumentResponse getDocumentPrivacy() {
        var doc = service.getActiveDocument("PRIVACY");
        return new LegalDocumentResponse(
                doc.getType(),
                doc.getVersion(),
                doc.getEffectiveDate(),
                doc.getContent()
        );
    }
}

