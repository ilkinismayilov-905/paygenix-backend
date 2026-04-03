package com.example.paygenix.controller;

import com.example.paygenix.dto.ApiResponseData;
import com.example.paygenix.dto.request.LoginRequest;
import com.example.paygenix.dto.request.RefreshTokenRequest;
import com.example.paygenix.dto.response.JwtTokenResponse;
import com.example.paygenix.dto.response.UserMeResponse;
import com.example.paygenix.entity.User;
import com.example.paygenix.exceptions.UserNotLoginException;
import com.example.paygenix.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Authentication", description = "İstifadəçi girişi, token yenilənməsi və profil məlumatları üçün API-lar")
public class AuthController {

    private final AuthService authService;
    private final ClientRegistrationRepository clientRegistrationRepository;

    @Operation(summary = "Sistem salamlaması", description = "Servisin aktiv olub-olmadığını yoxlamaq üçün sadə endpoint.")
    @GetMapping("/")
    public String loginWeb() {
        return "Welcome to Paygenix!";
    }

    @Operation(summary = "Giriş (Login)", description = "İstifadəçi adı və şifrə ilə sistemə daxil olaraq JWT token əldə edir.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Giriş uğurludur"),
            @ApiResponse(responseCode = "401", description = "Yanlış məlumatlar", content = @Content)
    })
    @PostMapping("/login")
    public ResponseEntity<ApiResponseData<JwtTokenResponse>> login(@Valid @RequestBody LoginRequest request) {
        log.info("Login request received for user: {}", request.email());
        JwtTokenResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponseData.build(HttpStatus.OK, "Login successful", response));
    }

    @Operation(
            summary = "Cari istifadəçi məlumatları",
            description = "Token-ə əsasən sistemə daxil olmuş istifadəçinin profil məlumatlarını qaytarır.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @GetMapping("/me")
    public ResponseEntity<ApiResponseData<UserMeResponse>> me(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof User user) {
            UserMeResponse responseData = new UserMeResponse(
                    user.getId(),
                    user.getUsername(),
                    user.getRoles(),
                    user.getExpiresIn()
            );
            return ResponseEntity.ok(ApiResponseData.build(HttpStatus.OK, "User profile retrieved successfully", responseData));
        }
        throw new UserNotLoginException("User session is invalid or not found");
    }

    @Operation(summary = "Token yeniləmə (Refresh)", description = "Müddəti bitmiş Access Token-i yeniləmək üçün Refresh Token-dən istifadə edilir.")
    @PostMapping("/refresh-token")
    public ResponseEntity<ApiResponseData<JwtTokenResponse>> refreshToken(@Valid @RequestBody RefreshTokenRequest requestDTO) {
        log.info("Refresh token request received");
        JwtTokenResponse responseDTO = authService.refreshToken(requestDTO.refreshToken());
        return ResponseEntity.ok(ApiResponseData.build(HttpStatus.OK, "Token refreshed successfully", responseDTO));
    }

    @Operation(summary = "OAuth2 Provayderləri", description = "Sistemdə aktiv olan xarici giriş provayderlərinin (Google, GitHub və s.) siyahısını qaytarır.")
    @GetMapping("/providers")
    public ResponseEntity<ApiResponseData<List<String>>> getOAuth2Providers() {
        List<String> providers = new ArrayList<>();
        if (clientRegistrationRepository instanceof Iterable) {
            for (ClientRegistration registration : (Iterable<ClientRegistration>) clientRegistrationRepository) {
                providers.add(registration.getRegistrationId());
            }
        }
        return ResponseEntity.ok(ApiResponseData.build(HttpStatus.OK, "OAuth2 providers retrieved successfully", providers));
    }
}