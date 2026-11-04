package com.example.paygenix.security;

import com.example.paygenix.dto.response.JwtTokenResponse;
import com.example.paygenix.entity.User;
import com.jayway.jsonpath.JsonPath;
import com.example.paygenix.support.IntegrationTest;
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

        cookieRefresh(login.refreshToken(), ORIGIN).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_REUSED"));
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

    private org.springframework.test.web.servlet.ResultActions cookieRefresh(String token, String origin) throws Exception {
        var req = post("/api/auth/refresh-token").cookie(new Cookie(RefreshCookie.NAME, token));
        if (origin != null) req.header("Origin", origin);
        return mockMvc.perform(req);
    }
}
