package com.example.paygenix.security;

import com.example.paygenix.entity.User;
import com.example.paygenix.service.CustomUserDetailsService;
import com.example.paygenix.service.JwtService;
import com.example.paygenix.service.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Date;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;
    private final UserService userService;

//    @Override
//    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
//
//        String authorizationHeader = request.getHeader("Authorization");
//        String token = null;
//        String username = null;
//
//
//        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
//            token = authorizationHeader.substring(7);
//            try {
//                username = jwtService.extractUsername(token);
//            } catch (Exception e) {
//                log.error("JWT token validation error: {}", e.getMessage());
//            }
//        }
//        String tokenType;
//        String subject;
//
//        try {
//            tokenType = jwtService.extractClaim(token, claims -> claims.get("scope", String.class));
//            subject = jwtService.extractUsername(token); // sub
//        } catch (Exception e) {
//            log.error("JWT parse error: {}", e.getMessage());
//            filterChain.doFilter(request, response);
//            return;
//        }
//
//        if ("onboarding".equals(tokenType)) {
//            filterChain.doFilter(request, response);
//            return;
//        }
//
//        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
//            UserDetails userDetails = userDetailsService.loadUserByUsername(username);
//            if (jwtService.validateToken(token, userDetails)) {
//                UsernamePasswordAuthenticationToken authToken
//                        = new UsernamePasswordAuthenticationToken(userDetails,
//                        null,
//                        userDetails.getAuthorities());
//                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
//                SecurityContextHolder.getContext().setAuthentication(authToken);
//            }
//            User user = userDetailsService.loadUserByUsername(subject);
//
//            if (jwtService.validateToken(token, user)) {
//
//                UsernamePasswordAuthenticationToken authentication =
//                        new UsernamePasswordAuthenticationToken(
//                                user,
//                                null,
//                                user.getAuthorities()
//                        );
//
//                SecurityContextHolder.getContext().setAuthentication(authentication);
//            }
//        }
//        filterChain.doFilter(request, response);
//    }
//
//    @Override
//    protected boolean shouldNotFilter(HttpServletRequest request) {
//        return request.getRequestURI().startsWith("/api/onboarding");
//    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");
        String path = request.getRequestURI();

        if(path.startsWith("/api/stripe/test/") || path.equals("/actuator/health")){
            filterChain.doFilter(request, response);
            return;
        }

        if (authHeader == null
                || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);

        try {
            String tokenUse = jwtService.extractTokenUse(token);
            boolean onboardingPath = path.startsWith("/api/onboarding/");

            if (JwtService.ONBOARDING.equals(tokenUse) && onboardingPath) {
                // Onboarding tokenində subject adətən "onboarding" və ya müvəqqəti ID olur
                String subject = jwtService.extractUsername(token);

                // Onboarding üçün müvəqqəti Authority yaradırıq
                SimpleGrantedAuthority authority = new SimpleGrantedAuthority("onboarding");

                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                        subject, null, Collections.singletonList(authority));

                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(auth);

                log.info("Onboarding token authenticated for: {}", subject);
                filterChain.doFilter(request, response);
                return;
            }

            if (!JwtService.ACCESS.equals(tokenUse) || onboardingPath) {
                // refresh, onboarding outside its flow, or legacy tokens without token_use
                filterChain.doFilter(request, response);
                return;
            }

            String username = jwtService.extractUsername(token);

            if (username != null &&
                    SecurityContextHolder.getContext().getAuthentication() == null) {

                UserDetails userDetails =
                        userDetailsService.loadUserByUsername(username);

                if (jwtService.validateToken(token, userDetails)) {

                    if (userDetails instanceof User user) {
                        Date expirationDate = jwtService.extractExpiration(token);
                        if (expirationDate != null) {
                            long secondsLeft = (expirationDate.getTime() - System.currentTimeMillis()) / 1000;
                            user.setExpiresIn(Math.max(secondsLeft, 0));
                            log.debug("Token for user {} expires in {} seconds", username, secondsLeft);
                        }
                    }

                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails,
                                    null,
                                    userDetails.getAuthorities()
                            );

                    authentication.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request)
                    );

                    SecurityContextHolder.getContext()
                            .setAuthentication(authentication);
                }
            }

        } catch (Exception e) {
            log.error("JWT authentication error: {}", e.getMessage());
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();

        boolean isEmailStep = path.equals("/api/onboarding/email");
        boolean isOtpStep = path.matches("/api/onboarding/[^/]+/otp");
        return isOtpStep || isEmailStep
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/api/auth/login")
                || path.startsWith("/api/auth/register");
    }

}
