package com.example.paygenix.controller;

import com.example.paygenix.dto.ApiResponseData;
import com.example.paygenix.dto.request.LegalAcceptanceRequest;
import com.example.paygenix.entity.User;
import com.example.paygenix.service.LegalAcceptanceService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
@RequestMapping("/api/v1/legal")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Legal Documents", description = "APIs for retrieving active Terms of Service and Privacy Policy")
public class LegalDocumentController {

    private final LegalDocumentService service;
    private final LegalAcceptanceService acceptanceService;

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
        return ResponseEntity.ok(ApiResponseData.success(service.getActiveDocument("TERMS")));
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
        return ResponseEntity.ok(ApiResponseData.success(service.getActiveDocument("PRIVACY")));
    }

    @Operation(
            summary = "Accept the current legal documents",
            description = "Records that the caller accepted the given Terms/Privacy versions (the ones listed in "
                    + "pendingLegalDocuments of GET /api/v1/auth/me). Until every active document is accepted, transfers and "
                    + "payments answer 403 LEGAL_ACCEPTANCE_REQUIRED. Accepting the same version again changes nothing.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Recorded"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "409", description = "A version is not the active one, or an active document is "
                    + "missing (LEGAL_VERSION_OUTDATED); nothing was recorded, load the documents again")
    })
    @PostMapping("/acceptances")
    public ResponseEntity<ApiResponseData<Void>> accept(@AuthenticationPrincipal User user,
                                                        @Valid @RequestBody LegalAcceptanceRequest request) {
        acceptanceService.accept(user.getId(), request.documents());
        return ResponseEntity.ok(ApiResponseData.success(null));
    }
}
