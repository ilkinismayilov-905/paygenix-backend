package com.example.paygenix.controller;

import com.example.paygenix.dto.ApiResponseData;
import com.example.paygenix.dto.request.KycRejectRequest;
import com.example.paygenix.dto.response.KycReviewItem;
import com.example.paygenix.dto.response.KycReviewPage;
import com.example.paygenix.entity.User;
import com.example.paygenix.enums.KycStatus;
import com.example.paygenix.service.KycService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/kyc")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN_ROLE')")
@Tag(name = "Admin: identity verification", description = "Review identity documents (admins only)")
@SecurityRequirement(name = "bearerAuth")
public class KycAdminController {

    private final KycService kycService;

    @Operation(summary = "Submissions by status", description = "PENDING oldest first, the others newest first. No document links here.")
    @GetMapping
    public ResponseEntity<ApiResponseData<KycReviewPage>> list(@RequestParam(defaultValue = "PENDING") KycStatus status,
                                                               @RequestParam(defaultValue = "0") @Min(0) int page,
                                                               @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return ResponseEntity.ok(ApiResponseData.success(kycService.list(status, page, size)));
    }

    @Operation(summary = "One submission", description = "documentUrl is a presigned link to the photo, valid for 5 minutes.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "The submission with its document link"),
            @ApiResponse(responseCode = "404", description = "No such submission (NOT_FOUND)"),
            @ApiResponse(responseCode = "503", description = "Document storage unavailable (MEDIA_UNAVAILABLE)")
    })
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponseData<KycReviewItem>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponseData.success(kycService.get(id)));
    }

    @Operation(summary = "Approve", description = "The user becomes verified and is told by email.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Approved"),
            @ApiResponse(responseCode = "403", description = "Your own submission (KYC_OWN_SUBMISSION)"),
            @ApiResponse(responseCode = "404", description = "No such submission (NOT_FOUND)"),
            @ApiResponse(responseCode = "409", description = "Already reviewed (KYC_ALREADY_REVIEWED) or being reviewed right now (RESOURCE_BUSY)")
    })
    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponseData<KycReviewItem>> approve(@AuthenticationPrincipal User admin, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponseData.success(kycService.approve(admin.getId(), id)));
    }

    @Operation(summary = "Reject", description = "reason (1-500 characters) is shown to the user and emailed; they may send a new document.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Rejected"),
            @ApiResponse(responseCode = "400", description = "No reason or longer than 500 characters (VALIDATION_FAILED)"),
            @ApiResponse(responseCode = "403", description = "Your own submission (KYC_OWN_SUBMISSION)"),
            @ApiResponse(responseCode = "404", description = "No such submission (NOT_FOUND)"),
            @ApiResponse(responseCode = "409", description = "Already reviewed (KYC_ALREADY_REVIEWED) or being reviewed right now (RESOURCE_BUSY)")
    })
    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponseData<KycReviewItem>> reject(@AuthenticationPrincipal User admin, @PathVariable Long id,
                                                                 @Valid @RequestBody KycRejectRequest request) {
        return ResponseEntity.ok(ApiResponseData.success(kycService.reject(admin.getId(), id, request.reason())));
    }
}
