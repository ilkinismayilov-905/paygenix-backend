package com.example.paygenix.controller;

import com.example.paygenix.dto.ApiResponseData;
import com.example.paygenix.dto.request.ChangePinRequest;
import com.example.paygenix.dto.request.OtpRequest;
import com.example.paygenix.dto.response.SuccessResponse;
import com.example.paygenix.service.OnboardingChangePinService;
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
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/change-pin")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "PIN Management", description = "APIs for secure PIN change workflow including OTP verification")
@SecurityRequirement(name = "bearerAuth")
public class ChangePinController {

    private final OnboardingChangePinService service;

    @Operation(
            summary = "Initiate PIN change",
            description = "Starts the PIN change process by sending an OTP to the authenticated user's email. Returns a Flow ID."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OTP sent and flow initiated successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - User session not found")
    })
    @PostMapping
    public ResponseEntity<ApiResponseData<String>> initiatePinChange(Authentication authentication) {
        String email = authentication.getName();
        log.info("Initiating PIN change flow for user: {}", email);

        String flowId = service.submitEmail(email);
        return ResponseEntity.ok(ApiResponseData.success(flowId));
    }

    @Operation(
            summary = "Verify OTP",
            description = "Verifies the One-Time Password (OTP) sent to the user's email for a specific flow."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OTP verified successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid or expired OTP"),
            @ApiResponse(responseCode = "404", description = "Flow ID not found")
    })
    @PostMapping("/{flowId}/otp")
    public ResponseEntity<SuccessResponse<Void>> verifyOtp(
            @Parameter(description = "Unique identifier for the PIN change process")
            @PathVariable String flowId,
            @Valid @RequestBody OtpRequest request
    ) {
        log.info("Verifying OTP for flow ID: {}", flowId);
        service.verifyOtp(flowId, request.otp());
        return ResponseEntity.ok(SuccessResponse.empty());
    }

    @Operation(
            summary = "Complete PIN change",
            description = "Updates the user's PIN after successful OTP verification. Requires the new PIN and its confirmation."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "PIN updated successfully"),
            @ApiResponse(responseCode = "400", description = "PINs do not match or invalid format"),
            @ApiResponse(responseCode = "403", description = "Action forbidden - OTP might not be verified yet")
    })
    @PostMapping("/{flowId}/pin")
    public ResponseEntity<SuccessResponse<Void>> changePin(
            @Parameter(description = "Unique identifier for the PIN change process")
            @PathVariable String flowId,
            @Valid @RequestBody ChangePinRequest request
    ) {
        log.info("Updating PIN for flow ID: {}", flowId);
        service.changePin(flowId, request.newPin(), request.reEnterNewPin());
        return ResponseEntity.ok(SuccessResponse.empty());
    }
}