package com.example.paygenix.controller;

import com.example.paygenix.dto.ApiResponseData;
import com.example.paygenix.dto.request.EditProfileRequest;
import com.example.paygenix.dto.request.EmailChangeRequest;
import com.example.paygenix.dto.request.EmailChangeConfirmRequest;
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
        log.info("Updating profile");

        EditProfileResponse response = editProfileService.editProfile(request, email);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponseData.build(
                HttpStatus.CREATED,
                "Profile updated successfully",
                response
        ));
    }

    @Operation(summary = "Start an email change", description = "Needs the current PIN. Sends a 6-digit code to the new address and another to the current one. "
            + "The email changes only after POST /api/profile/email/confirm with both codes (within 10 minutes); "
            + "the old address is then notified.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Codes sent to the new and the current address"),
            @ApiResponse(responseCode = "400", description = "Not an email address or PIN not 6 digits (VALIDATION_FAILED)"),
            @ApiResponse(responseCode = "403", description = "Wrong PIN (INVALID_PIN) or no PIN set yet (PIN_NOT_SET)"),
            @ApiResponse(responseCode = "409", description = "Email already taken (EMAIL_TAKEN); still counts toward the cooldown and daily limit"),
            @ApiResponse(responseCode = "429", description = "Code requested within the last minute (OTP_SEND_TOO_FREQUENT) "
                    + "or more than 5 changes today (EMAIL_CHANGE_LIMIT) or too many wrong PINs (PIN_LOCKED); see Retry-After")
    })
    @PostMapping("/email")
    public ResponseEntity<ApiResponseData<Void>> startEmailChange(@AuthenticationPrincipal User user,
                                                                  @Valid @RequestBody EmailChangeRequest request) {
        emailChangeService.start(user.getId(), request.email(), request.pin());
        return ResponseEntity.ok(ApiResponseData.success(null));
    }

    @Operation(summary = "Confirm an email change", description = "Needs the code sent to the new address (otp) and the one sent "
            + "to the current address (currentEmailOtp). Switches the account to the new email and signs out every device; "
            + "log in again with the new email.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Email changed, all sessions revoked"),
            @ApiResponse(responseCode = "400", description = "A wrong code, either one (INVALID_OTP), or no pending change (OTP_EXPIRED)"),
            @ApiResponse(responseCode = "409", description = "The email was taken meanwhile (EMAIL_TAKEN)"),
            @ApiResponse(responseCode = "429", description = "Too many wrong codes; start again (TOO_MANY_ATTEMPTS)")
    })
    @PostMapping("/email/confirm")
    public ResponseEntity<ApiResponseData<Void>> confirmEmailChange(@AuthenticationPrincipal User user,
                                                                    @Valid @RequestBody EmailChangeConfirmRequest request) {
        emailChangeService.confirm(user.getId(), request.otp(), request.currentEmailOtp());
        return ResponseEntity.ok(ApiResponseData.success(null));
    }

    @Operation(summary = "Get my profile", description = "The caller's own profile. Before the first save only fullName and email are set; "
            + "photoUrl is a 10-minute link to the photo, if there is one.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Profile of the authenticated user"),
            @ApiResponse(responseCode = "401", description = "Unauthorized access")
    })
    @GetMapping("/me")
    public ResponseEntity<ApiResponseData<EditProfileResponse>> getMyProfile(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponseData.success(editProfileService.getMine(user)));
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

    @Operation(summary = "Upload profile photo", description = "JPEG, PNG, WebP or GIF, at most 5 MB. Replaces the current "
            + "photo. Needs a saved profile. The response's photoUrl works for 10 minutes; GET /api/profile/me gives a fresh one.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Photo saved"),
            @ApiResponse(responseCode = "400", description = "No file or not an image (VALIDATION_FAILED)"),
            @ApiResponse(responseCode = "409", description = "No saved profile yet (PROFILE_REQUIRED)"),
            @ApiResponse(responseCode = "413", description = "Larger than 5 MB (PHOTO_TOO_LARGE)"),
            @ApiResponse(responseCode = "503", description = "Storage unavailable (MEDIA_UPLOAD_FAILED), with Retry-After")
    })
    @PostMapping(value = "/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponseData<EditProfileResponse>> uploadProfileImage(
            @Parameter(description = "The image file to upload") @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal User user) {
        EditProfileResponse response = editProfileService.editProfilePhoto(user.getId(), file);
        return ResponseEntity.ok(ApiResponseData.build(HttpStatus.OK, "Profile photo uploaded successfully", response));
    }

    @Operation(summary = "Remove profile photo", description = "Idempotent.")
    @DeleteMapping("/photo")
    public ResponseEntity<ApiResponseData<Void>> removeProfileImage(@AuthenticationPrincipal User user) {
        editProfileService.removeProfilePhoto(user.getId());
        return ResponseEntity.ok(ApiResponseData.success(null));
    }
}
