package com.example.paygenix.controller;

import com.example.paygenix.dto.ApiResponseData;
import com.example.paygenix.dto.response.StoryFeedResponse;
import com.example.paygenix.dto.response.StoryUploadResponse;
import com.example.paygenix.entity.User;
import com.example.paygenix.enums.MediaType;
import com.example.paygenix.enums.StoryVisibility;
import com.example.paygenix.service.StoryService;
import com.example.paygenix.service.StoryViewService;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/stories")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Stories", description = "APIs for uploading, viewing, and managing temporary stories")
@SecurityRequirement(name = "bearerAuth")
public class StoryController {

    private final StoryService storyService;
    private final StoryViewService storyViewService;
    private final UserService userService;

    @Operation(summary = "Upload a story", description = "Uploads an image or video story with specified visibility settings.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Story uploaded successfully"),
            @ApiResponse(responseCode = "400", description = "Unsupported file format or size"),
            @ApiResponse(responseCode = "401", description = "Unauthorized access")
    })
    @PostMapping(consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    // shortcut: still takes a Jwt, which is always null here (SEC-27), so upload stays a 500. Fixing it would expose
    // SEC-15 (client-chosen object keys). Rebuilt as admin-only promo upload in the stories task.
    public ResponseEntity<ApiResponseData<StoryUploadResponse>> uploadStory(
            @AuthenticationPrincipal Jwt jwt,
            @RequestPart("file") MultipartFile file,
            @RequestParam MediaType mediaType,
            @RequestParam(defaultValue = "FOLLOWERS") StoryVisibility visibility
    ) {
        Long userId = jwt.getClaim("userId");
        log.info("User {} is uploading a {} story", userId, mediaType);

        User user = userService.getById(userId);
        StoryUploadResponse response = storyService.uploadStory(user, file, mediaType, visibility);

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponseData.build(
                HttpStatus.CREATED,
                "Story uploaded successfully",
                response
        ));
    }

    @Operation(summary = "Get story feed", description = "Retrieves the stories of users that the current user follows.")
    @ApiResponse(responseCode = "200", description = "Story feed retrieved successfully")
    @GetMapping("/feed")
    public ResponseEntity<ApiResponseData<StoryFeedResponse>> getStoryFeed(
            @AuthenticationPrincipal User principal
    ) {
        Long userId = principal.getId();
        log.info("Fetching story feed for user ID: {}", userId);

        User user = userService.getById(userId);
        StoryFeedResponse response = storyService.getFeed(user);

        return ResponseEntity.ok(ApiResponseData.success(response));
    }

    @Operation(summary = "Mark story as viewed", description = "Records that the user has seen a specific story.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Story view recorded"),
            @ApiResponse(responseCode = "404", description = "Story not found")
    })
    @PostMapping("/{storyId}/view")
    public ResponseEntity<ApiResponseData<Void>> markStoryAsViewed(
            @PathVariable Long storyId,
            @AuthenticationPrincipal User principal
    ) {
        Long userId = principal.getId();
        log.info("User {} viewed story {}", userId, storyId);

        storyViewService.markViewed(storyId, userId);
        return ResponseEntity.ok(ApiResponseData.success(null));
    }

    @Operation(summary = "Delete a story", description = "Permanently removes a story if the current user is the owner.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Story deleted successfully"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Not the owner of the story"),
            @ApiResponse(responseCode = "404", description = "Story not found")
    })
    @DeleteMapping("/{storyId}")
    public ResponseEntity<ApiResponseData<Void>> deleteStory(
            @PathVariable Long storyId,
            @AuthenticationPrincipal User principal
    ) {
        Long userId = principal.getId();
        log.warn("User {} is deleting story {}", userId, storyId);

        User user = userService.getById(userId);
        storyService.deleteStory(user, storyId);

        return ResponseEntity.ok(ApiResponseData.success(null));
    }

    @Operation(summary = "Get media download URL", description = "Generates a temporary download link for story media (Cloud Storage).")
    @GetMapping("/media-download")
    public ResponseEntity<ApiResponseData<String>> getMediaDownloadUrl(
            @Parameter(description = "Storage object name/key") @RequestParam String objectName,
            @AuthenticationPrincipal User principal
    ) {
        Long userId = principal.getId();
        User user = userService.getById(userId);

        String url = storyService.getMediaDownloadUrl(user, objectName);
        return ResponseEntity.ok(ApiResponseData.success(url));
    }
}