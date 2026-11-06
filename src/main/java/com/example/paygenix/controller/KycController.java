package com.example.paygenix.controller;

import com.example.paygenix.dto.ApiResponseData;
import com.example.paygenix.dto.response.KycStatusResponse;
import com.example.paygenix.entity.User;
import com.example.paygenix.enums.KycDocumentType;
import com.example.paygenix.service.KycService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/kyc")
@RequiredArgsConstructor
@Tag(name = "Identity verification", description = "Send an identity document for manual review")
@SecurityRequirement(name = "bearerAuth")
public class KycController {

    private final KycService kycService;

    @Operation(summary = "My verification", description = "status: NOT_SUBMITTED, PENDING (waiting for review), APPROVED "
            + "(verified) or REJECTED (rejectionReason says why; send a new document). The other fields describe the latest submission.")
    @GetMapping("/me")
    public ResponseEntity<ApiResponseData<KycStatusResponse>> me(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponseData.success(kycService.status(user.getId())));
    }

    @Operation(summary = "Send an identity document", description = "multipart/form-data: documentType (ID_CARD, PASSPORT, "
            + "DRIVING_LICENCE) and document, one JPEG, PNG or WebP photo of at most 5 MB. It waits for an admin's review.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Sent; status is PENDING"),
            @ApiResponse(responseCode = "400", description = "Missing type or photo, or not JPEG/PNG/WebP (VALIDATION_FAILED)"),
            @ApiResponse(responseCode = "409", description = "Already verified (KYC_ALREADY_VERIFIED) or a document is waiting for review (KYC_ALREADY_PENDING)"),
            @ApiResponse(responseCode = "413", description = "Photo larger than 5 MB (DOCUMENT_TOO_LARGE)"),
            @ApiResponse(responseCode = "429", description = "More than 5 documents today (KYC_SUBMIT_LIMIT), with Retry-After"),
            @ApiResponse(responseCode = "503", description = "Document storage unavailable (MEDIA_UPLOAD_FAILED), with Retry-After")
    })
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponseData<KycStatusResponse>> submit(@AuthenticationPrincipal User user,
                                                                     @RequestParam KycDocumentType documentType,
                                                                     @RequestPart("document") MultipartFile document) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponseData.build(HttpStatus.CREATED, "Sent for review", kycService.submit(user.getId(), documentType, document)));
    }
}
