package com.example.paygenix.controller;

import com.example.paygenix.dto.ApiResponseData;
import com.example.paygenix.dto.response.SessionResponse;
import com.example.paygenix.entity.User;
import com.example.paygenix.service.SessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth/sessions")
@RequiredArgsConstructor
@Tag(name = "Sessions", description = "Signed-in devices of the current user")
@SecurityRequirement(name = "bearerAuth")
public class SessionController {

    private final SessionService sessionService;

    @Operation(summary = "List active sessions", description = "One entry per signed-in device; `current` marks the one making this request.")
    @GetMapping
    public ResponseEntity<ApiResponseData<List<SessionResponse>>> list(
            @AuthenticationPrincipal User user,
            @RequestAttribute(SessionService.SESSION_ID_ATTRIBUTE) UUID currentSessionId) {
        return ResponseEntity.ok(ApiResponseData.success(sessionService.list(user.getId(), currentSessionId)));
    }

    @Operation(summary = "Revoke one session", description = "Signs that device out: its refresh and access tokens stop working at once. Revoking the current session is a logout.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Session revoked"),
            @ApiResponse(responseCode = "404", description = "No active session with this id for this user")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponseData<Void>> revoke(@AuthenticationPrincipal User user, @PathVariable UUID id) {
        sessionService.revoke(user.getId(), id);
        return ResponseEntity.ok(ApiResponseData.success(null));
    }

    @Operation(summary = "Revoke all sessions", description = "Signs out every device, including this one.")
    @DeleteMapping
    public ResponseEntity<ApiResponseData<Void>> revokeAll(@AuthenticationPrincipal User user) {
        sessionService.revokeAll(user.getId());
        return ResponseEntity.ok(ApiResponseData.success(null));
    }
}
