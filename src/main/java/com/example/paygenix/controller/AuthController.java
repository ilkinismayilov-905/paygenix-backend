package com.example.paygenix.controller;

import com.example.paygenix.dto.request.LoginRequest;
import com.example.paygenix.dto.request.PinRequest;
import com.example.paygenix.dto.request.RefreshTokenRequest;
import com.example.paygenix.dto.response.JwtTokenResponse;
import com.example.paygenix.dto.response.UserMeResponse;
import com.example.paygenix.entity.User;
import com.example.paygenix.enums.Role;
import com.example.paygenix.repository.UserRepository;
import com.example.paygenix.service.AuthService;
import com.example.paygenix.service.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@CrossOrigin
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;
    private final ClientRegistrationRepository clientRegistrationRepository;
    private final UserRepository userRepository;


    @GetMapping("/")
    public String loginWeb() {
        return "Welcome to Paygenix!";
    }

    @PostMapping("/login")
    public ResponseEntity<JwtTokenResponse> login( @RequestBody LoginRequest request) {
        JwtTokenResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }
    @GetMapping("/loginForm")
    public String loginPage() {
        return """
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>Login</title>
            <style>
                body {
                    font-family: Arial, sans-serif;
                    background-color: #f0f2f5;
                    display: flex;
                    justify-content: center;
                    align-items: center;
                    height: 100vh;
                    margin: 0;
                }
                .login-container {
                    background-color: #fff;
                    padding: 40px;
                    border-radius: 10px;
                    box-shadow: 0 4px 15px rgba(0,0,0,0.2);
                    width: 300px;
                    text-align: center;
                }
                .login-container h2 {
                    margin-bottom: 20px;
                    color: #333;
                }
                .login-container input {
                    width: 100%;
                    padding: 10px;
                    margin: 10px 0;
                    border: 1px solid #ccc;
                    border-radius: 5px;
                    box-sizing: border-box;
                }
                .login-container button {
                    width: 100%;
                    padding: 10px;
                    background-color: #007bff;
                    color: white;
                    border: none;
                    border-radius: 5px;
                    cursor: pointer;
                    font-size: 16px;
                }
                .login-container button:hover {
                    background-color: #0056b3;
                }
            </style>
        </head>
        <body>
            <div class="login-container">
                <h2>Login</h2>
                <form method='post' action='/auth/login'>
                    <input name='username' placeholder='Email' required /><br/>
                    <input type='password' name='password' placeholder='Password' required /><br/>
                    <button type='submit'>Login</button>
                </form>
            </div>
        </body>
        </html>
        """;
    }

    @GetMapping("/me")
    public UserMeResponse me(Authentication authentication) {

        String email = authentication.getName();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Set<Role> roles = user.getRoles();

        long expiresIn = 0;

        if (authentication.getPrincipal() instanceof Jwt jwt) {
            Instant expiresAt = jwt.getExpiresAt();

            if (expiresAt != null) {
                expiresIn = Duration.between(Instant.now(), expiresAt).getSeconds();
                expiresIn = Math.max(expiresIn, 0); // negative olmasın
            }
        }

        return new UserMeResponse(
                user.getId(),
                user.getUsername(),   // səndə email qaytarır
                roles,
                expiresIn
        );
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<JwtTokenResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest requestDTO){

        try {
            log.info("Refresh token request received");
            JwtTokenResponse responseDTO = authService.refreshToken(requestDTO.refreshToken());
            log.info("Refresh token successful");

            return ResponseEntity.ok(responseDTO);

        }catch (Exception e) {
            log.error("Refresh token failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(JwtTokenResponse.builder()
                            .build());
        }
    }


//    @GetMapping("/get")
//    public Map<String, Object> getCurrentUser(Authentication authentication, HttpServletRequest request) {
//        // Authorization header-i yoxlayaq
//        String authHeader = request.getHeader("Authorization");
//        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
//            return Map.of("error", "Missing or invalid Authorization header");
//        }
//
//        String token = authHeader.substring(7);
//        String username = jwtService.extractUsername(token);
//        Date expiration = jwtService.extractExpiration(token);
//        long expiresIn = (expiration.getTime() - System.currentTimeMillis()) / 1000;
//
//        // Rolları müəyyənləşdiririk
//        List<String> roles = new ArrayList<>();
//        if (authentication != null && authentication.getPrincipal() != null) {
//            Object principal = authentication.getPrincipal();
//
//            if (principal instanceof UserDetails userDetails) {
//                for (GrantedAuthority authority : userDetails.getAuthorities()) {
//                    roles.add(authority.getAuthority());
//                }
//            } else if (principal instanceof com.example.paygenix.entity.User user) {
//                // Əgər sənin entity `User` sinfin `UserDetails` implement edibsə:
//                user.getAuthorities().forEach(a -> roles.add(a.getAuthority()));
//            }
//        }
//
//        Map<String, Object> response = new LinkedHashMap<>();
//        response.put("email", username);
//        response.put("roles", roles);
//        response.put("expiresIn", Math.max(expiresIn, 0));
//
//        return response;
//    }


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
