package com.example.paygenix.controller;

import com.example.paygenix.dto.ApiResponseData;
import com.example.paygenix.dto.response.LegalDocumentResponse;
import com.example.paygenix.service.LegalDocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/legal")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Legal Documents", description = "APIs for retrieving active Terms of Service and Privacy Policy")
public class LegalDocumentController {

    private final LegalDocumentService service;

    @Operation(
            summary = "Get Terms of Service",
            description = "Retrieves the latest active version of the Terms and Conditions."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Terms of Service retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Active terms document not found")
    })
    @GetMapping("/terms")
    public ResponseEntity<ApiResponseData<LegalDocumentResponse>> getDocumentTerms() {
        log.info("Fetching active Terms of Service");
        var doc = service.getActiveDocument("TERMS");

        LegalDocumentResponse docResponse = new LegalDocumentResponse(
                doc.getType(),
                doc.getVersion(),
                doc.getEffectiveDate(),
                doc.getContent()
        );

        return ResponseEntity.ok(ApiResponseData.success(docResponse));
    }

    @Operation(
            summary = "Get Privacy Policy",
            description = "Retrieves the latest active version of the Privacy Policy."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Privacy Policy retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Active privacy document not found")
    })
    @GetMapping("/privacy")
    public ResponseEntity<ApiResponseData<LegalDocumentResponse>> getDocumentPrivacy() {
        log.info("Fetching active Privacy Policy");
        var doc = service.getActiveDocument("PRIVACY");

        LegalDocumentResponse docResponse = new LegalDocumentResponse(
                doc.getType(),
                doc.getVersion(),
                doc.getEffectiveDate(),
                doc.getContent()
        );

        return ResponseEntity.ok(ApiResponseData.success(docResponse));
    }
}