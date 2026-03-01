package com.example.paygenix.controller;

import com.example.paygenix.dto.ApiResponse;
import com.example.paygenix.dto.request.LoginRequest;
import com.example.paygenix.dto.request.PinRequest;
import com.example.paygenix.dto.request.RefreshTokenRequest;
import com.example.paygenix.dto.response.JwtTokenResponse;
import com.example.paygenix.dto.response.UserMeResponse;
import com.example.paygenix.entity.User;
import com.example.paygenix.enums.Role;
import com.example.paygenix.exceptions.UserNotLoginException;
import com.example.paygenix.repository.UserRepository;
import com.example.paygenix.service.AuthService;
import com.example.paygenix.service.JwtService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
@SecurityRequirement(name = "bearerAuth")
public class AuthController {

    private final AuthService authService;
    private final ClientRegistrationRepository clientRegistrationRepository;


    @GetMapping("/")
    public String loginWeb() {
        return "Welcome to Paygenix!";
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<JwtTokenResponse>> login(@RequestBody LoginRequest request) {
        JwtTokenResponse response = authService.login(request);

        return ResponseEntity.ok(ApiResponse.build(
                HttpStatus.OK,
                "Login successful",
                response
        ));
    }
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserMeResponse>> me(Authentication authentication) {
        // Principal-ın tipini yoxlayırıq və User obyektinə cast edirik
        if (authentication != null && authentication.getPrincipal() instanceof User user) {

            UserMeResponse responseData = new UserMeResponse(
                    user.getId(),
                    user.getUsername(),
                    user.getRoles(),
                    user.getExpiresIn()
            );
            return ResponseEntity.ok(ApiResponse.build(
                    HttpStatus.OK,
                    "User profile retrieved successfully",
                    responseData
            ));
        }

        // Əgər nəsə səhv gedibsə (bu hissəyə normalda girməməlidir, çünki filter qoruyur)
        throw new UserNotLoginException("User session is invalid or not found");
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<ApiResponse<JwtTokenResponse>> refreshToken(@Valid @RequestBody RefreshTokenRequest requestDTO) {
        log.info("Refresh token request received");
        JwtTokenResponse responseDTO = authService.refreshToken(requestDTO.refreshToken());
        log.info("Refresh token successful");

        return ResponseEntity.ok(ApiResponse.build(
                HttpStatus.OK,
                "Token refreshed successfully",
                responseDTO
        ));
    }


    @GetMapping("/providers")
    public ResponseEntity<ApiResponse<List<String>>> getOAuth2Providers() {
        List<String> providers = new ArrayList<>();

        if (clientRegistrationRepository instanceof Iterable) {
            for (ClientRegistration registration : (Iterable<ClientRegistration>) clientRegistrationRepository) {
                providers.add(registration.getRegistrationId());
            }
        }

        return ResponseEntity.ok(ApiResponse.build(
                HttpStatus.OK,
                "OAuth2 providers retrieved successfully",
                providers
        ));
    }


}
