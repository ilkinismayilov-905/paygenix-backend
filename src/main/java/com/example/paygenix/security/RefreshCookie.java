package com.example.paygenix.security;

import com.example.paygenix.exceptions.InvalidRefreshTokenException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

/**
 * The refresh token as an httpOnly cookie scoped to /api/auth (ADR 0001), so page scripts never see it.
 * CSRF protection is off for the API, so a refresh that relies on the cookie must also come from an allowed Origin.
 */
@Component
public class RefreshCookie {

    public static final String NAME = "pg_refresh";
    private static final String PATH = "/api/auth";

    private final Duration maxAge;
    private final boolean secure;
    private final List<String> allowedOrigins;

    public RefreshCookie(@Value("${jwt.refresh-expiration}") long refreshSeconds,
                         @Value("${app.refresh-cookie.secure:true}") boolean secure,
                         @Value("${app.cors.allowed-origins}") List<String> corsOrigins,
                         @Value("${app.frontend-url}") String frontendUrl) {
        this.maxAge = Duration.ofSeconds(refreshSeconds);
        this.secure = secure;
        // the app itself is served same-origin in production, so its own origin counts too
        this.allowedOrigins = Stream.concat(corsOrigins.stream(), Stream.of(frontendUrl)).toList();
    }

    public String issue(String refreshToken) {
        return build(refreshToken, maxAge);
    }

    public String clear() {
        return build("", Duration.ZERO);
    }

    public void checkOrigin(String origin) {
        if (origin == null || !allowedOrigins.contains(origin)) {
            throw InvalidRefreshTokenException.originRejected();
        }
    }

    private String build(String value, Duration age) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true).secure(secure).sameSite("Strict").path(PATH).maxAge(age)
                .build().toString();
    }
}
