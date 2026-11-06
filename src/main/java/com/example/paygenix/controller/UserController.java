package com.example.paygenix.controller;

import com.example.paygenix.dto.request.SetPinRequest;
import com.example.paygenix.entity.User;
import jakarta.validation.Valid;
import com.example.paygenix.dto.ApiResponseData;
import com.example.paygenix.dto.response.UserResponse;
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
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "User Management", description = "APIs for user data retrieval and generic media file handling")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserService userService;

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


}