package com.example.paygenix.security;

import com.example.paygenix.entity.User;
import com.example.paygenix.enums.Role;
import com.example.paygenix.repository.UserRepository;
import com.example.paygenix.service.CustomUserDetailsService;
import com.example.paygenix.service.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final CustomUserDetailsService userDetailsService;
    private final JwtService jwtUtil;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException {

        OAuth2User oAuthUser = (OAuth2User) authentication.getPrincipal();

        String email = oAuthUser.getAttribute("email");
        String name = oAuthUser.getAttribute("name");

        if (email == null) {
            email = oAuthUser.getAttribute("login"); // GitHub email gizlidirsə
        }
        if (name == null) {
            name = oAuthUser.getAttribute("name");
        }

        log.info("OAuth2 login successful: {}", email);

        // 1. User DB-də varsa götür
        Optional<User> existing = userRepository.findByEmail(email);

        User user;
        if (existing.isPresent()) {
            user = existing.get();
        } else {
            // 2. DB-də yoxdursa yarat
            user = new User();
            user.setEmail(email);
            user.setFullname(name != null ? name : "OAuth User");
            user.setRoles(Set.of(Role.ROLE_USER));
            userRepository.save(user);
        }

        // 3. Spring Security UserDetails yarat
        UserDetails userDetails = userDetailsService.loadUserByUsername(email);

//        String accessToken = jwtUtil.generateToken(
//                userDetails,
//                user.getId(),
//                user.getFullname()
//        );
        Long userId = user.getId();
        String fullName = user.getFullname();
        String token = jwtUtil.generateJwtToken(userDetails, userId, fullName);
        String refreshToken = jwtUtil.generateJwtRefreshToken(userDetails);

        String redirectUrl = "http://localhost:8080/oauth2/success"
                + "?token=" + token
                + "&refreshToken=" + refreshToken;

        response.sendRedirect(redirectUrl);
        response.setContentType("application/json");
        response.getWriter().write("{\"token\": \"" + token + "\"}");

    }
}