package com.example.paygenix.controller;

import com.example.paygenix.dto.ApiResponseData;
import com.example.paygenix.dto.request.VerifyRequest;
import com.example.paygenix.service.VerifyAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/verify")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Account Verification", description = "APIs for verifying user identity and uploading verification documents")
@SecurityRequirement(name = "bearerAuth")
public class VerifyAccountController {

    private final VerifyAccountService verifyAccountService;

    @Operation(
            summary = "Submit verification details",
            description = "Submits the required parameters and details to verify the user's account."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Verification parameters processed successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid verification details provided"),
            @ApiResponse(responseCode = "401", description = "Unauthorized access")
    })
    @PostMapping
    public ResponseEntity<ApiResponseData<Void>> verifyAccount(
            @Valid @RequestBody VerifyRequest request,
            Authentication authentication
    ) {
        String email = authentication.getName();
        log.info("Processing account verification details for user: {}", email);

        verifyAccountService.verify(request, email);

        return ResponseEntity.ok(ApiResponseData.build(
                HttpStatus.OK,
                "Parameters verification has been successfully completed",
                null
        ));
    }

    @Operation(
            summary = "Upload verification photo",
            description = "Uploads an identity document or selfie for the final step of account verification."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Verification photo uploaded successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid file format or empty file")
    })
    @PostMapping(value = "/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponseData<Void>> uploadVerificationImage(
            @Parameter(description = "Identity document or photo to upload")
            @RequestPart("file") MultipartFile file,
            Authentication authentication
    ) {
        String email = authentication.getName();
        log.info("Uploading verification photo for user: {}", email);

        verifyAccountService.uploadVerificationImage(file, email);

        return ResponseEntity.ok(ApiResponseData.build(
                HttpStatus.OK,
                "User verification photo has been successfully uploaded",
                null
        ));
    }
}