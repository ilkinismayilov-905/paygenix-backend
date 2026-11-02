package com.example.paygenix.controller;

import com.example.paygenix.dto.request.SetPinRequest;
import com.example.paygenix.entity.User;
import jakarta.validation.Valid;
import com.example.paygenix.dto.ApiResponseData;
import com.example.paygenix.dto.response.UserResponse;
import com.example.paygenix.service.MediaService;
import com.example.paygenix.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "User Management", description = "APIs for user data retrieval and generic media file handling")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserService userService;
    private final MediaService mediaService;

    @Operation(
            summary = "Get all users",
            description = "Retrieves a comprehensive list of all registered users in the system."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User list retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized access")
    })
    @PreAuthorize("hasRole('ADMIN_ROLE')")
    @GetMapping
    public ResponseEntity<ApiResponseData<List<UserResponse>>> getAllUsers() {
        log.info("Fetching all users from the system");
        List<UserResponse> list = userService.getAll();
        return ResponseEntity.ok(ApiResponseData.success(list));
    }

    @Operation(summary = "Set the first transaction PIN",
            description = "For Google accounts, which are created without a PIN. Payments and transfers answer 403 PIN_NOT_SET until it is set.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "PIN set"),
            @ApiResponse(responseCode = "400", description = "PIN is not 6 digits"),
            @ApiResponse(responseCode = "409", description = "PIN already set (PIN_ALREADY_SET); use the change-PIN flow")
    })
    @PostMapping("/pin")
    public ResponseEntity<ApiResponseData<Void>> setFirstPin(@AuthenticationPrincipal User user,
                                                             @Valid @RequestBody SetPinRequest request) {
        userService.setFirstPin(user.getId(), request.pin());
        return ResponseEntity.ok(ApiResponseData.success(null));
    }

    @Operation(
            summary = "Upload media file",
            description = "Uploads a generic media file (e.g., document, image) to the cloud storage and returns its generated object key (uploads/{userId}/{uuid}.{ext})."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Media uploaded successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid file name"),
            @ApiResponse(responseCode = "503", description = "Media storage unavailable (MEDIA_UPLOAD_FAILED); see Retry-After")
    })
    @PostMapping(value = "/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponseData<String>> uploadMedia(
            @Parameter(description = "The media file to be uploaded")
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal User user
    ) {
        String objectKey = mediaService.upload(file, user.getId());
        return ResponseEntity.ok(ApiResponseData.success(objectKey));
    }

    @Operation(
            summary = "Get media download URL",
            description = "Generates a temporary or direct download URL for a previously uploaded media object."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Download URL generated successfully"),
            @ApiResponse(responseCode = "404", description = "Media object not found"),
            @ApiResponse(responseCode = "503", description = "Media storage unavailable (MEDIA_UNAVAILABLE); see Retry-After")
    })
    @GetMapping("/media-download")
    public ResponseEntity<ApiResponseData<String>> downloadMedia(
            @Parameter(description = "The unique storage object name/key", example = "uploads/1/3f2b9c1e-7a4d-4e8a-9b1c-2d5e6f7a8b9c.jpg")
            @RequestParam String objectName
    ) {
        log.info("Generating download URL for media object: {}", objectName);
        String downloadUrl = mediaService.getDownloadUrl(objectName);
        return ResponseEntity.ok(ApiResponseData.success(downloadUrl));
    }
}