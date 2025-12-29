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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

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

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);

        try {
            String tokenType = jwtService.extractClaim(token, c -> c.get("scope", String.class));

            // onboarding token-ları burax
            if ("onboarding".equals(tokenType)) {
                filterChain.doFilter(request, response);
                return;
            }

            String username = jwtService.extractUsername(token);

            if (username != null &&
                    SecurityContextHolder.getContext().getAuthentication() == null) {

                UserDetails userDetails =
                        userDetailsService.loadUserByUsername(username);

                if (jwtService.validateToken(token, userDetails)) {

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
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/api/onboarding");
    }

}
