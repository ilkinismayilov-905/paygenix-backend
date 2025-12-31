package com.example.paygenix.controller;

import com.example.paygenix.dto.ApiResponse;
import com.example.paygenix.dto.request.EditProfileRequest;
import com.example.paygenix.dto.request.EditProfileResponse;
import com.example.paygenix.service.EditProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class EditProfileController {
    public final EditProfileService editProfileService;

    @PostMapping
    public ResponseEntity<ApiResponse<EditProfileResponse>> create(@RequestBody @Valid EditProfileRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();
        return ResponseEntity.ok(ApiResponse.build(

                HttpStatus.CREATED, "Profile edited successfully",
                editProfileService.editProfile(request, email)
        ));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<EditProfileResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.build(
                HttpStatus.OK,"Getting all data successfully",
                editProfileService.getAll()
        ));
    }
}
