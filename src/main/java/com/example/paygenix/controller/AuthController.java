package com.example.paygenix.controller;

import com.example.paygenix.dto.request.LoginRequest;
import com.example.paygenix.dto.response.JwtTokenResponse;
import com.example.paygenix.dto.response.UserMeResponse;
import com.example.paygenix.service.AuthService;
import com.example.paygenix.service.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
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
@CrossOrigin
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;
    private final ClientRegistrationRepository clientRegistrationRepository;


    @GetMapping("/")
    public String loginWeb() {
        return "Welcome to Paygenix!";
    }

    @PostMapping("/login")
    public ResponseEntity<JwtTokenResponse> login( @RequestBody LoginRequest request) {
        JwtTokenResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    public UserMeResponse me(@AuthenticationPrincipal Jwt jwt) {
        Set<String> roles = new HashSet<>(
                jwt.getClaimAsStringList("roles")
        );

        return new UserMeResponse(
                jwt.getSubject(),
                roles,
                jwt.getExpiresAt()
        );
    }


    @GetMapping("/providers")
    public List<String> getOAuth2Providers() {

        List<String> providers = new ArrayList<>();

        if (clientRegistrationRepository instanceof Iterable) {
            for (ClientRegistration registration :
                    (Iterable<ClientRegistration>) clientRegistrationRepository) {
                providers.add(registration.getRegistrationId());
            }
        }


        return providers;
    }
}
