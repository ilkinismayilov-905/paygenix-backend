package com.example.paygenix.controller;

import com.example.paygenix.dto.request.ChangePinRequest;
import com.example.paygenix.dto.request.OtpRequest;
import com.example.paygenix.dto.response.SuccessResponse;
import com.example.paygenix.service.OnboardingChangePinService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/change-pin")
@RequiredArgsConstructor
@Slf4j
public class ChangePinController {

    private final OnboardingChangePinService service;

    @PostMapping
    public ResponseEntity<SuccessResponse<String>> submitEmail(Authentication authentication) {

        String email = authentication.getName();
        String flowId = service.submitEmail(email);

        return ResponseEntity.ok(
                SuccessResponse.of(flowId)
        );
    }

    @PostMapping("/{flowId}/otp")
    public ResponseEntity<SuccessResponse<Void>> verifyOtp(
            @PathVariable String flowId,
            @RequestBody OtpRequest request
    ) {
        service.verifyOtp(flowId, request.otp());
        return ResponseEntity.ok(SuccessResponse.empty());
    }

    @PostMapping("/{flowId}/pin")
    public ResponseEntity<SuccessResponse<Void>> changePin(
            @PathVariable String flowId,
            @RequestBody ChangePinRequest request
    ) {
        service.changePin(flowId, request.newPin(), request.reEnterNewPin());
        return ResponseEntity.ok(SuccessResponse.empty());
    }

}
