package com.example.paygenix.controller;

import com.example.paygenix.dto.ApiResponseData;
import com.example.paygenix.dto.response.PromoStoryResponse;
import com.example.paygenix.entity.User;
import com.example.paygenix.service.StoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.List;

@RestController
@RequestMapping("/api/admin/stories")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN_ROLE')")
@Tag(name = "Admin: promo stories", description = "Publish and take down promo stories (admins only)")
@SecurityRequirement(name = "bearerAuth")
public class StoryAdminController {

    private final StoryService storyService;

    @Operation(summary = "Live promo stories", description = "Newest first, with how many users viewed each.")
    @GetMapping
    public ResponseEntity<ApiResponseData<List<PromoStoryResponse>>> live() {
        return ResponseEntity.ok(ApiResponseData.success(storyService.livePromos()));
    }

    @Operation(summary = "Publish a promo story", description = "multipart/form-data: file (JPEG, PNG, WebP, GIF, MP4, MOV or "
            + "WebM, at most 10 MB); hours it stays up, 1 to 168 (default 24). Every user sees it on the dashboard.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Published"),
            @ApiResponse(responseCode = "400", description = "No file, unsupported type or hours out of range (VALIDATION_FAILED)"),
            @ApiResponse(responseCode = "413", description = "File larger than 10 MB"),
            @ApiResponse(responseCode = "503", description = "Storage unavailable (MEDIA_UPLOAD_FAILED), with Retry-After")
    })
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponseData<PromoStoryResponse>> publish(@AuthenticationPrincipal User admin,
                                                                       @RequestPart("file") MultipartFile file,
                                                                       @RequestParam(defaultValue = "24") @Min(1) @Max(168) int hours) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponseData.build(HttpStatus.CREATED, "Published",
                storyService.publish(admin, file, Duration.ofHours(hours))));
    }

    @Operation(summary = "Take a promo story down", description = "It disappears from every dashboard; its file is removed.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Taken down"),
            @ApiResponse(responseCode = "404", description = "No such promo story, or already taken down (NOT_FOUND)")
    })
    @DeleteMapping("/{storyId}")
    public ResponseEntity<ApiResponseData<Void>> delete(@PathVariable Long storyId) {
        storyService.delete(storyId);
        return ResponseEntity.ok(ApiResponseData.success(null));
    }
}
