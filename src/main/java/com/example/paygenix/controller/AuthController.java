package com.example.paygenix.controller;

import com.example.paygenix.dto.ApiResponseData;
import com.example.paygenix.dto.request.LoginRequest;
import com.example.paygenix.dto.request.OAuthExchangeRequest;
import com.example.paygenix.dto.request.RefreshTokenRequest;
import com.example.paygenix.dto.response.JwtTokenResponse;
import com.example.paygenix.dto.response.UserMeResponse;
import com.example.paygenix.entity.User;
import com.example.paygenix.exceptions.UserNotLoginException;
import com.example.paygenix.service.AuthService;
import com.example.paygenix.service.SessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
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
    private final SessionService sessionService;
    private final ClientRegistrationRepository clientRegistrationRepository;

    @Operation(summary = "Sistem salamlaması", description = "Servisin aktiv olub-olmadığını yoxlamaq üçün sadə endpoint.")
    @GetMapping("/")
    public String loginWeb() {
        return "Welcome to Paygenix!";
    }

    @Operation(summary = "Giriş (Login)", description = "İstifadəçi adı və şifrə ilə sistemə daxil olaraq JWT token əldə edir.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Giriş uğurludur"),
            @ApiResponse(responseCode = "401", description = "Yanlış məlumatlar", content = @Content),
            @ApiResponse(responseCode = "429", description = "More than 5 attempts for this email in 15 minutes (LOGIN_LOCKED)", content = @Content)
    })
    @PostMapping("/login")
    public ResponseEntity<ApiResponseData<JwtTokenResponse>> login(
            @Valid @RequestBody LoginRequest request,
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
        log.info("Login request received");
        JwtTokenResponse response = authService.login(request, userAgent);
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

    @Operation(summary = "Token yeniləmə (Refresh)", description = "Rotates the refresh token: the old one stops working. "
            + "Presenting an already rotated refresh token revokes the whole session.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "New access and refresh token"),
            @ApiResponse(responseCode = "401", description = "Invalid, expired or revoked (INVALID_REFRESH_TOKEN), or reused (REFRESH_TOKEN_REUSED)", content = @Content)
    })
    @PostMapping("/refresh-token")
    public ResponseEntity<ApiResponseData<JwtTokenResponse>> refreshToken(@Valid @RequestBody RefreshTokenRequest requestDTO) {
        log.info("Refresh token request received");
        JwtTokenResponse responseDTO = sessionService.refresh(requestDTO.refreshToken());
        return ResponseEntity.ok(ApiResponseData.build(HttpStatus.OK, "Token refreshed successfully", responseDTO));
    }

    @Operation(summary = "Google sign-in: exchange code", description = "After Google sign-in the browser lands on "
            + "FRONTEND_URL/oauth2/callback?code=... The frontend trades the code here, once, within 60 seconds, for the same tokens as /login. "
            + "On failure it lands on ?error=email_not_verified | account_exists | oauth_failed instead.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Signed in; a new device session was started"),
            @ApiResponse(responseCode = "401", description = "Code invalid, expired or already used (INVALID_OAUTH_CODE)", content = @Content)
    })
    @PostMapping("/oauth/exchange")
    public ResponseEntity<ApiResponseData<JwtTokenResponse>> exchangeOAuthCode(
            @Valid @RequestBody OAuthExchangeRequest request,
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
        JwtTokenResponse response = authService.loginWithOAuthCode(request.code(), userAgent);
        return ResponseEntity.ok(ApiResponseData.build(HttpStatus.OK, "Login successful", response));
    }

    @Operation(summary = "OAuth2 Provayderləri", description = "Sistemdə aktiv olan xarici giriş provayderlərinin (Google) siyahısını qaytarır.")
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