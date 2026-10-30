package com.example.paygenix.config;

import com.example.paygenix.service.GoogleOidcUserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.Set;

// Sends the browser back to the frontend with a short error code instead of the server's /login page.
@Component
public class OAuth2FailureHandler extends SimpleUrlAuthenticationFailureHandler {

    private static final Set<String> KNOWN_ERRORS =
            Set.of(GoogleOidcUserService.EMAIL_NOT_VERIFIED, GoogleOidcUserService.ACCOUNT_EXISTS);

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        String error = exception instanceof OAuth2AuthenticationException oauth
                && KNOWN_ERRORS.contains(oauth.getError().getErrorCode())
                ? oauth.getError().getErrorCode() : "oauth_failed";
        getRedirectStrategy().sendRedirect(request, response,
                UriComponentsBuilder.fromUriString(frontendUrl).path("/oauth2/callback")
                        .queryParam("error", error).build().toUriString());
    }
}
