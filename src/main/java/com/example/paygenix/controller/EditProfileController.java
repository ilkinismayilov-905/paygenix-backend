package com.example.paygenix.controller;

import com.example.paygenix.dto.ApiResponseData;
import com.example.paygenix.dto.request.EditProfileRequest;
import com.example.paygenix.dto.request.EmailRequest;
import com.example.paygenix.dto.request.OtpRequest;
import com.example.paygenix.dto.response.EditProfileResponse;
import com.example.paygenix.entity.User;
import com.example.paygenix.service.EditProfileService;
import com.example.paygenix.service.EmailChangeService;
import com.example.paygenix.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Profile Management", description = "APIs for updating user profiles and profile pictures")
@SecurityRequirement(name = "bearerAuth")
public class EditProfileController {

    private final EditProfileService editProfileService;
    private final UserService userService;
    private final EmailChangeService emailChangeService;

    @Operation(summary = "Update profile details", description = "Updates basic profile information for the authenticated user.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Profile updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid profile data provided"),
            @ApiResponse(responseCode = "401", description = "Unauthorized access")
    })
    @PostMapping
    public ResponseEntity<ApiResponseData<EditProfileResponse>> updateProfile(
            @Valid @RequestBody EditProfileRequest request,
            Authentication authentication) {

        String email = authentication.getName();
        log.info("Updating profile for user: {}", email);

        EditProfileResponse response = editProfileService.editProfile(request, email);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponseData.build(
                HttpStatus.CREATED,
                "Profile updated successfully",
                response
        ));
    }

    @Operation(summary = "Start an email change", description = "Sends a 6-digit code to the new address. "
            + "The email changes only after POST /api/profile/email/confirm with that code (within 10 minutes).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Code sent to the new address"),
            @ApiResponse(responseCode = "400", description = "Not an email address (VALIDATION_FAILED)"),
            @ApiResponse(responseCode = "409", description = "Email already in use (EMAIL_IN_USE)"),
            @ApiResponse(responseCode = "429", description = "Code requested within the last minute (OTP_SEND_TOO_FREQUENT) "
                    + "or more than 5 changes today (EMAIL_CHANGE_LIMIT); see Retry-After")
    })
    @PostMapping("/email")
    public ResponseEntity<ApiResponseData<Void>> startEmailChange(@AuthenticationPrincipal User user,
                                                                  @Valid @RequestBody EmailRequest request) {
        emailChangeService.start(user.getId(), request.email());
        return ResponseEntity.ok(ApiResponseData.success(null));
    }

    @Operation(summary = "Confirm an email change", description = "Switches the account to the new email and signs out every device; log in again with the new email.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Email changed, all sessions revoked"),
            @ApiResponse(responseCode = "400", description = "Wrong code (INVALID_OTP) or no pending change (OTP_EXPIRED)"),
            @ApiResponse(responseCode = "409", description = "The email was taken meanwhile (EMAIL_IN_USE)"),
            @ApiResponse(responseCode = "429", description = "Too many wrong codes; start again (TOO_MANY_ATTEMPTS)")
    })
    @PostMapping("/email/confirm")
    public ResponseEntity<ApiResponseData<Void>> confirmEmailChange(@AuthenticationPrincipal User user,
                                                                    @Valid @RequestBody OtpRequest request) {
        emailChangeService.confirm(user.getId(), request.otp());
        return ResponseEntity.ok(ApiResponseData.success(null));
    }

    @Operation(summary = "Get all profiles", description = "Fetches a list of all profiles (Admin/Internal use).")
    @ApiResponse(responseCode = "200", description = "Profiles retrieved successfully")
    @PreAuthorize("hasRole('ADMIN_ROLE')")
    @GetMapping
    public ResponseEntity<ApiResponseData<List<EditProfileResponse>>> getAllProfiles() {
        log.info("Fetching all profiles");
        List<EditProfileResponse> profiles = editProfileService.getAll();
        return ResponseEntity.ok(ApiResponseData.build(
                HttpStatus.OK,
                "Profiles retrieved successfully",
                profiles
        ));
    }

    @Operation(
            summary = "Upload profile photo",
            description = "Uploads or updates the profile picture for the authenticated user."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Profile photo uploaded successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid file format or size"),
            @ApiResponse(responseCode = "500", description = "Internal server error during file upload")
    })
    @PostMapping(value = "/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponseData<EditProfileResponse>> uploadProfileImage(
            @Parameter(description = "The image file to upload (PNG, JPG, etc.)")
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal User user) {

        Long userId = user.getId();
        log.info("Uploading profile photo for user ID: {}", userId);

        // Note: UserService call is here for fetching user if needed,
        // but passing userId directly to service is usually cleaner.
        EditProfileResponse response = editProfileService.editProfilePhoto(userId, file);

        return ResponseEntity.ok(ApiResponseData.build(
                HttpStatus.OK,
                "Profile photo uploaded successfully",
                response
        ));
    }
}