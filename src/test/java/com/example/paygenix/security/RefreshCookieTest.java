package com.example.paygenix.security;

import com.example.paygenix.dto.response.JwtTokenResponse;
import com.example.paygenix.entity.User;
import com.jayway.jsonpath.JsonPath;
import com.example.paygenix.service.OAuthCodeService;
import com.example.paygenix.support.IntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// T-109, ADR 0001: refresh token in an httpOnly cookie; cookie refreshes need an allowed Origin.
class RefreshCookieTest extends IntegrationTest {

    private static final String ORIGIN = "http://localhost:3000";

    @Autowired private OAuthCodeService oAuthCodeService;

    @Test
    void loginSetsAHardenedCookieScopedToAuth() throws Exception {
        User user = createUser();
        MockHttpServletResponse res = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + user.getEmail() + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse();

        String cookie = res.getHeader("Set-Cookie");
        assertThat(cookie).startsWith(RefreshCookie.NAME + "=" + JsonPath.read(res.getContentAsString(), "$.data.refreshToken") + ";")
                .contains("Path=/api/auth", "HttpOnly", "Secure", "SameSite=Strict", "Max-Age=604800");
    }

    @Test
    void refreshWithTheCookieRotatesItAndTheOldOneIsReuse() throws Exception {
        JwtTokenResponse login = sessionService.start(createUser(), "browser");

        MockHttpServletResponse res = cookieRefresh(login.refreshToken(), ORIGIN)
                .andExpect(status().isOk()).andReturn().getResponse();
        String rotated = JsonPath.read(res.getContentAsString(), "$.data.refreshToken");
        assertThat(res.getHeader("Set-Cookie")).startsWith(RefreshCookie.NAME + "=" + rotated + ";");
        assertThat(rotated).isNotEqualTo(login.refreshToken());

        String cleared = cookieRefresh(login.refreshToken(), ORIGIN).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_REUSED"))
                .andReturn().getResponse().getHeader("Set-Cookie");
        assertThat(cleared).as("a dead cookie is dropped").startsWith(RefreshCookie.NAME + "=;").contains("Max-Age=0");
    }

    @Test
    void aBodyTokenWinsOverTheCookieAndNeedsNoOrigin() throws Exception {
        JwtTokenResponse login = sessionService.start(createUser(), "app");
        mockMvc.perform(post("/api/auth/refresh-token").cookie(new Cookie(RefreshCookie.NAME, "garbage"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + login.refreshToken() + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void googleCodeExchangeSetsTheCookie() throws Exception {
        String code = oAuthCodeService.issue(createUser().getId());
        String cookie = mockMvc.perform(post("/api/auth/oauth/exchange").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getHeader("Set-Cookie");
        assertThat(cookie).startsWith(RefreshCookie.NAME + "=").contains("HttpOnly", "SameSite=Strict");
    }

    @Test
    void cookieRefreshFromAForeignOrMissingOriginIsRejected() throws Exception {
        JwtTokenResponse login = sessionService.start(createUser(), "browser");

        // a foreign origin is already stopped by the CORS filter
        cookieRefresh(login.refreshToken(), "https://evil.example").andExpect(status().isForbidden());
        cookieRefresh(login.refreshToken(), null).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_ORIGIN_REJECTED"));
        // the rejected attempts did not burn the token
        cookieRefresh(login.refreshToken(), ORIGIN).andExpect(status().isOk());
    }

    @Test
    void noBodyAndNoCookieIsInvalid() throws Exception {
        mockMvc.perform(post("/api/auth/refresh-token").header("Origin", ORIGIN))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void signingOutThisDeviceClearsTheCookieButRevokingAnotherDoesNot() throws Exception {
        User user = createUser();
        JwtTokenResponse here = sessionService.start(user, "browser");
        JwtTokenResponse other = sessionService.start(user, "phone");

        String otherCleared = mockMvc.perform(delete("/api/auth/sessions/" + jwtService.extractSessionId(other.token()))
                        .header("Authorization", "Bearer " + here.token()))
                .andExpect(status().isOk()).andReturn().getResponse().getHeader("Set-Cookie");
        assertThat(otherCleared).isNull();

        String cleared = mockMvc.perform(delete("/api/auth/sessions/" + jwtService.extractSessionId(here.token()))
                        .header("Authorization", "Bearer " + here.token()))
                .andExpect(status().isOk()).andReturn().getResponse().getHeader("Set-Cookie");
        assertThat(cleared).startsWith(RefreshCookie.NAME + "=;").contains("Max-Age=0", "Path=/api/auth");
    }

    @Test
    void signingOutEverywhereClearsTheCookie() throws Exception {
        JwtTokenResponse login = sessionService.start(createUser(), "browser");
        String cleared = mockMvc.perform(delete("/api/auth/sessions").header("Authorization", "Bearer " + login.token()))
                .andExpect(status().isOk()).andReturn().getResponse().getHeader("Set-Cookie");
        assertThat(cleared).startsWith(RefreshCookie.NAME + "=;").contains("Max-Age=0");
    }

    @Test
    void logoutRevokesTheCookiesSessionAndClearsIt() throws Exception {
        User user = createUser();
        JwtTokenResponse here = sessionService.start(user, "browser");
        JwtTokenResponse other = sessionService.start(user, "phone");

        String cleared = mockMvc.perform(post("/api/auth/logout").header("Origin", ORIGIN)
                        .cookie(new Cookie(RefreshCookie.NAME, here.refreshToken())))
                .andExpect(status().isOk()).andReturn().getResponse().getHeader("Set-Cookie");
        assertThat(cleared).startsWith(RefreshCookie.NAME + "=;").contains("Max-Age=0", "Path=/api/auth");

        assertThat(sessionService.isActive(jwtService.extractSessionId(here.token()), user.getId())).isFalse();
        assertThat(sessionService.isActive(jwtService.extractSessionId(other.token()), user.getId())).isTrue();
        cookieRefresh(here.refreshToken(), ORIGIN).andExpect(status().isUnauthorized());

        // idempotent: signing out again with the same cookie is still a 200 that clears it
        assertThat(mockMvc.perform(post("/api/auth/logout").header("Origin", ORIGIN)
                        .cookie(new Cookie(RefreshCookie.NAME, here.refreshToken())))
                .andExpect(status().isOk()).andReturn().getResponse().getHeader("Set-Cookie")).contains("Max-Age=0");
    }

    @Test
    void logoutWithoutAValidCookieStillClearsIt() throws Exception {
        JwtTokenResponse login = sessionService.start(createUser(), "browser");
        for (Cookie c : new Cookie[]{null, new Cookie(RefreshCookie.NAME, "garbage"),
                new Cookie(RefreshCookie.NAME, login.token())}) { // an access token is not a refresh token
            var req = post("/api/auth/logout").header("Origin", ORIGIN);
            if (c != null) req.cookie(c);
            assertThat(mockMvc.perform(req).andExpect(status().isOk()).andReturn().getResponse().getHeader("Set-Cookie"))
                    .contains("Max-Age=0");
        }
        cookieRefresh(login.refreshToken(), ORIGIN).andExpect(status().isOk());
    }

    @Test
    void logoutWithoutAnAllowedOriginIsRejectedAndKeepsTheSession() throws Exception {
        JwtTokenResponse login = sessionService.start(createUser(), "browser");
        mockMvc.perform(post("/api/auth/logout").cookie(new Cookie(RefreshCookie.NAME, login.refreshToken())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_ORIGIN_REJECTED"));
        cookieRefresh(login.refreshToken(), ORIGIN).andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions cookieRefresh(String token, String origin) throws Exception {
        var req = post("/api/auth/refresh-token").cookie(new Cookie(RefreshCookie.NAME, token));
        if (origin != null) req.header("Origin", origin);
        return mockMvc.perform(req);
    }
}
