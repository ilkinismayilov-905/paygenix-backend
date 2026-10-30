package com.example.paygenix.security;

import com.example.paygenix.dto.response.JwtTokenResponse;
import com.example.paygenix.entity.User;
import com.example.paygenix.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Decision #13, SEC-18: per-device sessions, refresh rotation with reuse detection, revoke one / all.
class DeviceSessionTest extends IntegrationTest {

    @Test
    void refreshRotatesTheToken() throws Exception {
        JwtTokenResponse login = sessionService.start(createUser(), "phone");

        String rotated = refresh(login.refreshToken()).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String newRefresh = JsonPath.read(rotated, "$.data.refreshToken");

        // issued in the same second as the login token, still different (was a unique-key 500 before jti)
        assertThat(newRefresh).isNotEqualTo(login.refreshToken());
        refresh(newRefresh).andExpect(status().isOk());
    }

    @Test
    void reusingARotatedTokenRevokesTheSession() throws Exception {
        JwtTokenResponse login = sessionService.start(createUser(), "phone");
        String rotated = refresh(login.refreshToken()).andReturn().getResponse().getContentAsString();
        String newRefresh = JsonPath.read(rotated, "$.data.refreshToken");
        String newAccess = JsonPath.read(rotated, "$.data.token");

        refresh(login.refreshToken()).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_REUSED"));

        refresh(newRefresh).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
        assertThat(meStatus("Bearer " + newAccess)).isIn(302, 401);
    }

    @Test
    void onlyTheHashOfTheRefreshTokenIsStored() throws Exception {
        JwtTokenResponse login = sessionService.start(createUser(), "phone");
        List<String> stored = jdbc.queryForList(
                "SELECT refresh_token_hash FROM user_sessions WHERE refresh_token_hash = ? OR refresh_token_hash = ?",
                String.class, login.refreshToken(), sha256(login.refreshToken()));
        assertThat(stored).containsExactly(sha256(login.refreshToken()));
    }

    @Test
    void listShowsEachDeviceAndMarksTheCurrentOne() throws Exception {
        User user = createUser();
        JwtTokenResponse phone = sessionService.start(user, "phone");
        sessionService.start(user, "laptop");

        mockMvc.perform(get("/api/auth/sessions").header("Authorization", "Bearer " + phone.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[?(@.deviceName == 'phone')].current").value(true))
                .andExpect(jsonPath("$.data[?(@.deviceName == 'laptop')].current").value(false));
    }

    @Test
    void revokingOneSessionSignsOutOnlyThatDevice() throws Exception {
        User user = createUser();
        JwtTokenResponse phone = sessionService.start(user, "phone");
        JwtTokenResponse laptop = sessionService.start(user, "laptop");

        mockMvc.perform(delete("/api/auth/sessions/" + jwtService.extractSessionId(laptop.token()))
                        .header("Authorization", "Bearer " + phone.token()))
                .andExpect(status().isOk());

        assertThat(meStatus("Bearer " + laptop.token())).isIn(302, 401);
        refresh(laptop.refreshToken()).andExpect(status().isUnauthorized());
        assertThat(meStatus("Bearer " + phone.token())).isEqualTo(200);
    }

    @Test
    void cannotRevokeAnotherUsersSession() throws Exception {
        JwtTokenResponse victim = sessionService.start(createUser(), "phone");

        mockMvc.perform(delete("/api/auth/sessions/" + jwtService.extractSessionId(victim.token()))
                        .header("Authorization", bearer(createUser())))
                .andExpect(status().isNotFound());

        assertThat(meStatus("Bearer " + victim.token())).isEqualTo(200);
    }

    @Test
    void revokeAllSignsOutEveryDevice() throws Exception {
        User user = createUser();
        JwtTokenResponse phone = sessionService.start(user, "phone");
        JwtTokenResponse laptop = sessionService.start(user, "laptop");
        JwtTokenResponse other = sessionService.start(createUser(), "phone");

        mockMvc.perform(delete("/api/auth/sessions").header("Authorization", "Bearer " + phone.token()))
                .andExpect(status().isOk());

        assertThat(meStatus("Bearer " + phone.token())).isIn(302, 401);
        assertThat(meStatus("Bearer " + laptop.token())).isIn(302, 401);
        refresh(laptop.refreshToken()).andExpect(status().isUnauthorized());
        assertThat(meStatus("Bearer " + other.token())).isEqualTo(200);
    }

    @Test
    void loginRecordsTheUserAgentAsDeviceName() throws Exception {
        User user = createUser();
        String body = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .header("User-Agent", "Paygenix-iOS/1.0")
                        .content("{\"email\":\"" + user.getEmail() + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String access = JsonPath.read(body, "$.data.token");

        mockMvc.perform(get("/api/auth/sessions").header("Authorization", "Bearer " + access))
                .andExpect(jsonPath("$.data[0].deviceName").value("Paygenix-iOS/1.0"));
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/auth/refresh-token").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refreshToken + "\"}"));
    }

    private int meStatus(String authorization) throws Exception {
        return mockMvc.perform(get("/api/auth/me").header("Authorization", authorization))
                .andReturn().getResponse().getStatus();
    }

    private static String sha256(String value) throws NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
