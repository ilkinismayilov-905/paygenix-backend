package com.example.paygenix.controller;

import com.example.paygenix.dto.ApiResponseData;
import com.example.paygenix.dto.request.*;
import com.example.paygenix.service.OnboardingService;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/onboarding")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Onboarding", description = "APIs for user registration and onboarding workflow")
public class OnboardingController {

    private final OnboardingService service;

    @Operation(summary = "Submit email", description = "Starts the onboarding process by submitting an email and initiating a flow.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Email submitted, flow ID generated"),
            @ApiResponse(responseCode = "400", description = "Invalid email format")
    })
    @PostMapping("/email")
    public ResponseEntity<ApiResponseData<String>> submitEmail(@Valid @RequestBody EmailRequest request) {
        log.info("Starting onboarding for email: {}", request.email());
        String flowId = service.submitEmail(request.email());
        return ResponseEntity.ok(ApiResponseData.success(flowId));
    }

    @Operation(summary = "Verify OTP", description = "Verifies the One-Time Password sent to the user's email.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OTP verified successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid or expired OTP")
    })
    @PostMapping("/{flowId}/otp")
    public ResponseEntity<ApiResponseData<String>> verifyOtp(
            @Parameter(description = "The unique flow ID") @PathVariable String flowId,
            @Valid @RequestBody OtpRequest request) {
        log.info("Verifying OTP for flow ID: {}", flowId);
        String result = service.verifyOtp(flowId, request.otp());
        return ResponseEntity.ok(ApiResponseData.success(result));
    }

    @Operation(summary = "Set full name", description = "Updates the user's full name in the onboarding flow.")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasAuthority('onboarding')")
    @PutMapping("/{flowId}/full-name")
    public ResponseEntity<ApiResponseData<Void>> setFullName(
            @PathVariable String flowId,
            @Valid @RequestBody FullNameRequest request) {
        log.info("Setting full name for flow ID: {}", flowId);
        service.setFullName(flowId, request.fullName());
        return ResponseEntity.ok(ApiResponseData.success(null));
    }

    @Operation(summary = "Set username", description = "Assigns a unique username to the user during onboarding.")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasAuthority('onboarding')")
    @PutMapping("/{flowId}/username")
    public ResponseEntity<ApiResponseData<Void>> setUsername(
            @PathVariable String flowId,
            @Valid @RequestBody UsernameRequest request) {
        log.info("Setting username for flow ID: {}", flowId);
        service.setUsername(flowId, request.username());
        return ResponseEntity.ok(ApiResponseData.success(null));
    }

    @Operation(summary = "Set PIN and complete onboarding", description = "Finalizes the onboarding with the login password and the 6-digit transaction PIN. The user then signs in through /api/auth/login with email and password.")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasAuthority('onboarding')")
    @PutMapping("/{flowId}/pin")
    public ResponseEntity<ApiResponseData<Void>> setPin(
            @PathVariable String flowId,
            @Valid @RequestBody PinRequest request) {
        log.info("Setting PIN and finalizing onboarding for flow ID: {}", flowId);
        service.setPin(flowId, request.pin(), request.password());
        return ResponseEntity.ok(ApiResponseData.success(null));
    }
}