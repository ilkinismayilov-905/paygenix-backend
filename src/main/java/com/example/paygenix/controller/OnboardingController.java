package com.example.paygenix.controller;

import com.example.paygenix.dto.ApiResponse;
import com.example.paygenix.dto.request.*;
import com.example.paygenix.dto.response.OnboardingTokenResponse;
import com.example.paygenix.service.OnboardingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/onboarding")
@RequiredArgsConstructor
public class OnboardingController {
    private final OnboardingService service;

    @PostMapping("/email")
    public ResponseEntity<ApiResponse<String>> email(@RequestBody EmailRequest r) {
        Map<String,Object> response = new HashMap<>();
        String dto = service.submitEmail(r.email());
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    @PostMapping("/{flowId}/otp")
    public ResponseEntity<ApiResponse<String>> otp(@PathVariable String flowId,
                      @RequestBody OtpRequest r) {

        String s = service.verifyOtp(flowId, r.otp());
       return ResponseEntity.ok(ApiResponse.success(s));
    }

    @PreAuthorize("hasAuthority('onboarding')")
    @PutMapping("/{flowId}/full-name")
    public void fullName(@PathVariable String flowId,
                         @RequestBody FullNameRequest r) {
        service.setFullName(flowId, r.fullName());
    }

    @PreAuthorize("hasAuthority('onboarding')")
    @PutMapping("/{flowId}/username")
    public void username(@PathVariable String flowId,
                         @RequestBody UsernameRequest r) {
        service.setUsername(flowId, r.username());
    }

    @PreAuthorize("hasAuthority('onboarding')")
    @PutMapping("/{flowId}/pin")
    public ResponseEntity<ApiResponse<OnboardingTokenResponse>> pin(@PathVariable String flowId,
                                                   @RequestBody PinRequest r) {
        OnboardingTokenResponse dto = service.setPin(flowId, r.pin());
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

}
