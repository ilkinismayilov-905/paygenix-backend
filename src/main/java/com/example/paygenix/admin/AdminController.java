package com.example.paygenix.admin;

import com.example.paygenix.dto.ApiResponseData;
import com.example.paygenix.entity.User;
import com.example.paygenix.enums.ReportStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN_ROLE')")
@Tag(name = "Admin: users and reports", description = "Find, lock and unlock users; work through problem reports (admins only)")
@SecurityRequirement(name = "bearerAuth")
public class AdminController {

    private final AdminService adminService;

    @Operation(summary = "Find users", description = "query matches part of the email or username (case-insensitive); newest first.")
    @GetMapping("/users")
    public ResponseEntity<ApiResponseData<AdminPage<AdminUserItem>>> users(
            @RequestParam(defaultValue = "") @Size(max = 100) String query,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return ResponseEntity.ok(ApiResponseData.success(adminService.users(query, page, size)));
    }

    @Operation(summary = "Lock a user", description = "Sign-in and every session stop at once. Not for yourself or other admins.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Locked"),
            @ApiResponse(responseCode = "403", description = "The user is an admin (ADMIN_TARGET)"),
            @ApiResponse(responseCode = "404", description = "No such user (NOT_FOUND)"),
            @ApiResponse(responseCode = "409", description = "Your own account (CANNOT_LOCK_SELF)")
    })
    @PostMapping("/users/{id}/lock")
    public ResponseEntity<ApiResponseData<AdminUserItem>> lock(@AuthenticationPrincipal User admin, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponseData.success(adminService.setLocked(admin, id, true)));
    }

    @Operation(summary = "Unlock a user")
    @PostMapping("/users/{id}/unlock")
    public ResponseEntity<ApiResponseData<AdminUserItem>> unlock(@AuthenticationPrincipal User admin, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponseData.success(adminService.setLocked(admin, id, false)));
    }

    @Operation(summary = "Problem reports by status", description = "Newest first.")
    @GetMapping("/reports")
    public ResponseEntity<ApiResponseData<AdminPage<AdminReportItem>>> reports(
            @RequestParam(defaultValue = "OPEN") ReportStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return ResponseEntity.ok(ApiResponseData.success(adminService.reports(status, page, size)));
    }

    @Operation(summary = "Set a report's status", description = "OPEN, IN_PROGRESS, RESOLVED or REJECTED.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Updated"),
            @ApiResponse(responseCode = "404", description = "No such report (NOT_FOUND)")
    })
    @PatchMapping("/reports/{id}")
    public ResponseEntity<ApiResponseData<AdminReportItem>> setStatus(@AuthenticationPrincipal User admin, @PathVariable Long id,
                                                                      @Valid @RequestBody ReportStatusRequest request) {
        return ResponseEntity.ok(ApiResponseData.success(adminService.setReportStatus(admin, id, request.status())));
    }
}
