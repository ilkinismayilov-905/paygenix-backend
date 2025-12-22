package com.example.paygenix.controller;

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
    public ResponseEntity<Map<String,Object>> email(@RequestBody EmailRequest r) {
        Map<String,Object> response = new HashMap<>();
        try {
            String dto = service.submitEmail(r.email());

            response.put("success", true);
            response.put("data", dto);

            return ResponseEntity.status(HttpStatus.OK).body(response);
        }catch (IllegalArgumentException e){
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
        catch (Exception e) {

            response.put("success", false);
            response.put("message", e.getMessage());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @PostMapping("/{flowId}/otp")
    public ResponseEntity<Map<String,Object>> otp(@PathVariable String flowId,
                      @RequestBody OtpRequest r) {

        Map<String,Object> response = new HashMap<>();
        try {
            String s = service.verifyOtp(flowId, r.otp());

            response.put("success", true);
            response.put("data", s);

            return ResponseEntity.status(HttpStatus.OK).body(response);
        }catch (IllegalArgumentException e){
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
        catch (Exception e) {

            response.put("success", false);
            response.put("message", e.getMessage());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @PreAuthorize("hasAuthority('SCOPE_onboarding')")
    @PutMapping("/{flowId}/full-name")
    public void fullName(@PathVariable String flowId,
                         @RequestBody FullNameRequest r) {
        service.setFullName(flowId, r.fullName());
    }

    @PreAuthorize("hasAuthority('SCOPE_onboarding')")
    @PutMapping("/{flowId}/username")
    public void username(@PathVariable String flowId,
                         @RequestBody UsernameRequest r) {
        service.setUsername(flowId, r.username());
    }

    @PreAuthorize("hasAuthority('SCOPE_onboarding')")
    @PutMapping("/{flowId}/pin")
    public ResponseEntity<Map<String, Object>> pin(@PathVariable String flowId,
                                                   @RequestBody PinRequest r) {
        Map<String,Object> response = new HashMap<>();
        try {
            OnboardingTokenResponse dto = service.setPin(flowId, r.pin());

            response.put("success", true);
            response.put("data", dto);

            return ResponseEntity.status(HttpStatus.OK).body(response);
        }catch (IllegalArgumentException e){
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
        catch (Exception e) {

            response.put("success", false);
            response.put("message", e.getMessage());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }

    }

}
