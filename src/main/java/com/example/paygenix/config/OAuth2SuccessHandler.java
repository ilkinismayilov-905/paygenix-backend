package com.example.paygenix.config;

import com.example.paygenix.entity.User;
import com.example.paygenix.service.GoogleOidcUserService;
import com.example.paygenix.service.OAuthCodeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

// Sends the browser back to the frontend with a one-time code, never with tokens (SEC-9).
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final GoogleOidcUserService googleOidcUserService;
    private final OAuthCodeService oAuthCodeService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        User user = googleOidcUserService.findOrCreate((OidcUser) authentication.getPrincipal());
        // the OAuth2 login left a server session behind; the API itself is stateless
        if (request.getSession(false) != null) {
            request.getSession(false).invalidate();
        }

        String targetUrl = UriComponentsBuilder.fromUriString(frontendUrl).path("/oauth2/callback")
                .queryParam("code", oAuthCodeService.issue(user.getId()))
                .build().toUriString();
        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}
