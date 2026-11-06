package com.example.paygenix.controller;

import com.example.paygenix.dto.ApiResponseData;
import com.example.paygenix.dto.response.StoryFeedResponse;
import com.example.paygenix.entity.User;
import com.example.paygenix.service.StoryService;
import com.example.paygenix.service.StoryViewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stories")
@RequiredArgsConstructor
@Tag(name = "Stories", description = "Promo stories on the dashboard")
@SecurityRequirement(name = "bearerAuth")
public class StoryController {

    private final StoryService storyService;
    private final StoryViewService storyViewService;

    @Operation(summary = "Promo stories", description = "At most one group (the app's own row) with the live promos, oldest "
            + "first. mediaUrl is a presigned link (STORY_PRESIGN_SECONDS); viewed tells whether the caller has seen it.")
    @GetMapping("/feed")
    public ResponseEntity<ApiResponseData<StoryFeedResponse>> getStoryFeed(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponseData.success(storyService.getFeed(user)));
    }

    @Operation(summary = "Mark a story as viewed", description = "Idempotent.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Story view recorded"),
            @ApiResponse(responseCode = "404", description = "No live promo story with this id (NOT_FOUND)")
    })
    @PostMapping("/{storyId}/view")
    public ResponseEntity<ApiResponseData<Void>> markStoryAsViewed(@PathVariable Long storyId, @AuthenticationPrincipal User user) {
        storyViewService.markViewed(storyId, user.getId());
        return ResponseEntity.ok(ApiResponseData.success(null));
    }
}
