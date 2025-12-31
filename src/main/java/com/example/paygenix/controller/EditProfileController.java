package com.example.paygenix.controller;

import com.example.paygenix.dto.ApiResponse;
import com.example.paygenix.dto.request.EditProfileRequest;
import com.example.paygenix.dto.response.EditProfileResponse;
import com.example.paygenix.entity.User;
import com.example.paygenix.enums.MediaType;
import com.example.paygenix.service.EditProfileService;
import com.example.paygenix.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class EditProfileController {
    private final EditProfileService editProfileService;
    private final UserService userService;

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

    @PostMapping(value = "/photo",consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<EditProfileResponse>> uploadProfileImage(
            @RequestPart("file") MultipartFile file,
                                                  @AuthenticationPrincipal Jwt jwt) {
        Long userId = jwt.getClaim("userId"); // və ya "sub"
        User user = userService.getById(userId);
        return ResponseEntity.ok(ApiResponse.build(
                HttpStatus.OK,"Profile photo added successfully",
                editProfileService.editProfilePhoto(userId, file)
        ));
    }

}
