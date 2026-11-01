package com.example.paygenix.security;

import com.example.paygenix.support.IntegrationTest;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// SEC-21 CORS allowlist, SEC-22 no form login / docs off by default, SEC-39 security headers.
class HttpHardeningTest extends IntegrationTest {

    @Test
    void unauthenticatedApiCallIs401NotARedirect() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void thereIsNoFormLogin() throws Exception {
        mockMvc.perform(post("/login").param("username", "a@example.test").param("password", "x"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/login")).andExpect(status().isUnauthorized());
    }

    @Test
    void apiDocsAreOffByDefault() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
    }

    @Test
    void allowedOriginPassesPreflightWithoutCredentials() throws Exception {
        mockMvc.perform(options("/api/transfer")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Authorization, Content-Type, Idempotency-Key"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
    }

    // browsers hide response headers from scripts unless they are exposed; the client needs Retry-After on 429s
    @Test
    void retryAfterIsExposedToAllowedOrigins() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                        .header("Origin", "http://localhost:3000")
                        .header("Authorization", bearer(createUser())))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Expose-Headers", "Retry-After"));
    }

    @Test
    void unknownOriginIsRefused() throws Exception {
        mockMvc.perform(options("/api/auth/login")
                        .header("Origin", "https://evil.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void responsesCarrySecurityHeaders() throws Exception {
        mockMvc.perform(get("/api/auth/me").header("Authorization", bearer(createUser())))
                .andExpect(status().isOk())
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("Content-Security-Policy", "frame-ancestors 'none'"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"));
    }
}
