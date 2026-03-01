package com.example.paygenix.controller;

import com.example.paygenix.dto.ApiResponse;
import com.example.paygenix.dto.response.LegalDocumentResponse;
import com.example.paygenix.entity.LegalDocument;
import com.example.paygenix.service.LegalDocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/legal")
@RequiredArgsConstructor
public class LegalDocumentController {

    private final LegalDocumentService service;

    @GetMapping("/terms")
    public ResponseEntity<ApiResponse<LegalDocumentResponse>> getDocumentTerms() {
        var doc = service.getActiveDocument("TERMS");
        LegalDocumentResponse docResponse = new LegalDocumentResponse(
                doc.getType(),
                doc.getVersion(),
                doc.getEffectiveDate(),
                doc.getContent()
        );

        return ResponseEntity.ok(ApiResponse.success(
                docResponse
        ));
    }
    @GetMapping("/privacy")
    @ExceptionHandler
    public ResponseEntity<ApiResponse<LegalDocumentResponse>> getDocumentPrivacy() {
        var doc = service.getActiveDocument("PRIVACY");
       LegalDocumentResponse docResponse = new LegalDocumentResponse(
                       doc.getType(),
                       doc.getVersion(),
                       doc.getEffectiveDate(),
                       doc.getContent()
               );

        return ResponseEntity.ok(ApiResponse.success(
                docResponse
        ));
    }
}

