package com.example.paygenix.controller;

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
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/user")
@CrossOrigin
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

    @Operation(
            summary = "Upload media file",
            description = "Uploads a generic media file (e.g., document, image) to the cloud storage and returns the object key/URL."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Media uploaded successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid file format or empty file")
    })
    @PostMapping(value = "/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponseData<String>> uploadMedia(
            @Parameter(description = "The media file to be uploaded")
            @RequestParam("file") MultipartFile file
    ) {
        log.info("Uploading media file: {}", file.getOriginalFilename());
        String objectKey = mediaService.upload(file);
        return ResponseEntity.ok(ApiResponseData.success(objectKey));
    }

    @Operation(
            summary = "Get media download URL",
            description = "Generates a temporary or direct download URL for a previously uploaded media object."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Download URL generated successfully"),
            @ApiResponse(responseCode = "404", description = "Media object not found")
    })
    @GetMapping("/media-download")
    public ResponseEntity<ApiResponseData<String>> downloadMedia(
            @Parameter(description = "The unique storage object name/key", example = "profile-images/user123.jpg")
            @RequestParam String objectName
    ) {
        log.info("Generating download URL for media object: {}", objectName);
        String downloadUrl = mediaService.getDownloadUrl(objectName);
        return ResponseEntity.ok(ApiResponseData.success(downloadUrl));
    }
}