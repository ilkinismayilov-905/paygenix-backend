package com.example.paygenix.controller;

import com.example.paygenix.dto.ApiResponse;
import com.example.paygenix.dto.response.StoryFeedResponse;
import com.example.paygenix.dto.response.StoryUploadResponse;
import com.example.paygenix.entity.User;
import com.example.paygenix.enums.MediaType;
import com.example.paygenix.enums.StoryVisibility;
import com.example.paygenix.service.StoryService;
import com.example.paygenix.service.StoryViewService;
import com.example.paygenix.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/stories")
@RequiredArgsConstructor
public class StoryController {

    private final StoryService storyService;
    private final StoryViewService storyViewService;
    private final UserService userService;


    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<StoryUploadResponse>> upload(
            @AuthenticationPrincipal Jwt jwt,
            @RequestPart("file") MultipartFile file,
            @RequestParam MediaType mediaType,
            @RequestParam(defaultValue = "FOLLOWERS") StoryVisibility visibility
    ) {
        Long userId = jwt.getClaim("userId"); // və ya "sub"
        User user = userService.getById(userId);
        StoryUploadResponse s = storyService.uploadStory(user, file, mediaType, visibility);
        return ResponseEntity.ok(ApiResponse.success(s));
    }

    @GetMapping("/feed")
    public ResponseEntity<ApiResponse<StoryFeedResponse>> feed(
            @AuthenticationPrincipal Jwt jwt
            ) {
        Long userId = jwt.getClaim("userId"); // və ya "sub"
        User user = userService.getById(userId);
        StoryFeedResponse s = storyService.getFeed(user);
        return ResponseEntity.ok(ApiResponse.success(s));
    }

    @PostMapping("/{storyId}/view")
    public void view(@PathVariable Long storyId,
                     @AuthenticationPrincipal Jwt jwt
    ) {
        Long userId = jwt.getClaim("userId"); // və ya "sub"
        User user = userService.getById(userId);
        storyViewService.markViewed(storyId,userId);
    }

    @DeleteMapping("/{storyId}")
    public void delete(@PathVariable Long storyId,
                       @AuthenticationPrincipal Jwt jwt
    ) {
        Long userId = jwt.getClaim("userId"); // və ya "sub"
        User user = userService.getById(userId);
        storyService.deleteStory(user, storyId);
    }

    @GetMapping("/media-download")
    public String mediaDownload(@RequestParam String objectName,
                                @AuthenticationPrincipal Jwt jwt
    ) {
        Long userId = jwt.getClaim("userId"); // və ya "sub"
        User user = userService.getById(userId);
        return storyService.getMediaDownloadUrl(user, objectName);
    }
}
