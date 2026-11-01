package com.example.paygenix.security;

import com.example.paygenix.entity.User;
import com.example.paygenix.service.CustomUserDetailsService;
import com.example.paygenix.service.JwtService;
import com.example.paygenix.service.SessionService;
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
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;
    private final SessionService sessionService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");
        String path = request.getRequestURI();

        if(path.equals("/actuator/health")){
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
                // SEC-19: an onboarding token may only drive the flow it was issued for
                String flowId = jwtService.extractClaim(token, claims -> claims.get(JwtService.FLOW_ID, String.class));
                if (flowId == null || !path.startsWith("/api/onboarding/" + flowId + "/")) {
                    filterChain.doFilter(request, response);
                    return;
                }

                // temporary authority that only unlocks the onboarding flow
                SimpleGrantedAuthority authority = new SimpleGrantedAuthority("onboarding");

                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                        flowId, null, Collections.singletonList(authority));

                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(auth);

                log.debug("Onboarding token authenticated for flow: {}", flowId);
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

                // a revoked or expired device session invalidates its access tokens at once
                // shortcut: one indexed PK lookup per request; cache with evict-on-revoke if it shows up in profiles
                UUID sessionId = jwtService.extractSessionId(token);
                if (jwtService.validateToken(token, userDetails)
                        && userDetails instanceof User user
                        && sessionId != null
                        && sessionService.isActive(sessionId, user.getId())) {
                    request.setAttribute(SessionService.SESSION_ID_ATTRIBUTE, sessionId);

                    Date expirationDate = jwtService.extractExpiration(token);
                    if (expirationDate != null) {
                        long secondsLeft = (expirationDate.getTime() - System.currentTimeMillis()) / 1000;
                        user.setExpiresIn(Math.max(secondsLeft, 0));
                        log.debug("Token for user {} expires in {} seconds", user.getId(), secondsLeft);
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
            log.debug("JWT authentication failed: {}", e.getMessage());
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
