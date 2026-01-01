package com.example.paygenix.controller;

import com.example.paygenix.dto.ApiResponse;
import com.example.paygenix.dto.request.VerifyRequest;
import com.example.paygenix.dto.response.EditProfileResponse;
import com.example.paygenix.entity.User;
import com.example.paygenix.service.VerifyAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/verify")
public class VerifyAccountController {
    private final VerifyAccountService verifyAccountService;

    @PostMapping()
    public ResponseEntity<String> verify(
            @RequestBody VerifyRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();
        verifyAccountService.verify(request, email);
        return ResponseEntity.status(HttpStatus.OK).body("Params verification has been successfully completed");

    }

    @PostMapping(value = "/photo",consumes = "multipart/form-data")
    public ResponseEntity<String> uploadProfileImage(
            @RequestPart("file") MultipartFile file) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();
        verifyAccountService.uploadVerificationImage(file, email);

        return ResponseEntity.status(HttpStatus.OK).body("User verification has been successfully completed");
    }

}
