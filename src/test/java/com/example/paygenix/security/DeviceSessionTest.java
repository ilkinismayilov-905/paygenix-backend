package com.example.paygenix.security;

import com.example.paygenix.dto.response.JwtTokenResponse;
import com.example.paygenix.entity.User;
import com.example.paygenix.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Decision #13, SEC-18: per-device sessions, refresh rotation with reuse detection, revoke one / all.
class DeviceSessionTest extends IntegrationTest {

    @Value("${jwt.session-max-lifetime}")
    private long maxLifetime;

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
    void expiredSessionCannotRefresh() throws Exception {
        JwtTokenResponse login = sessionService.start(createUser(), "phone");
        jdbc.update("UPDATE user_sessions SET expires_at = now() - interval '1 minute' WHERE id = ?",
                jwtService.extractSessionId(login.token()));

        refresh(login.refreshToken()).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
        assertThat(meStatus("Bearer " + login.token())).isIn(302, 401);
    }

    @Test
    void refreshNeverExtendsASessionPastItsMaxLifetime() throws Exception {
        JwtTokenResponse login = sessionService.start(createUser(), "phone");
        UUID id = jwtService.extractSessionId(login.token());
        jdbc.update("UPDATE user_sessions SET created_at = now() - interval '29 days' WHERE id = ?", id);

        refresh(login.refreshToken()).andExpect(status().isOk());

        Boolean capped = jdbc.queryForObject(
                "SELECT expires_at = created_at + make_interval(secs => ?) FROM user_sessions WHERE id = ?",
                Boolean.class, maxLifetime, id);
        assertThat(capped).isTrue();
    }

    @Test
    void sessionOlderThanMaxLifetimeCannotRefresh() throws Exception {
        JwtTokenResponse login = sessionService.start(createUser(), "phone");
        // expires_at still in the future, e.g. the cap was lowered after the last refresh
        jdbc.update("UPDATE user_sessions SET created_at = now() - make_interval(secs => ?) - interval '1 minute' WHERE id = ?",
                maxLifetime, jwtService.extractSessionId(login.token()));

        refresh(login.refreshToken()).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
        // nor can its access token be used
        assertThat(meStatus("Bearer " + login.token())).isIn(302, 401);
    }

    @Test
    void sessionsPastMaxLifetimeAreNotListed() throws Exception {
        User user = createUser();
        JwtTokenResponse phone = sessionService.start(user, "phone");
        JwtTokenResponse old = sessionService.start(user, "old-laptop");
        jdbc.update("UPDATE user_sessions SET created_at = now() - make_interval(secs => ?) - interval '1 minute' WHERE id = ?",
                maxLifetime, sessionId(old));

        mockMvc.perform(get("/api/auth/sessions").header("Authorization", "Bearer " + phone.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].deviceName").value("phone"));
    }

    @Test
    void purgeDeletesOnlySessionsEndedLongAgo() {
        User user = createUser();
        UUID oldRevoked = sessionId(sessionService.start(user, "a"));
        UUID oldExpired = sessionId(sessionService.start(user, "b"));
        UUID recentlyRevoked = sessionId(sessionService.start(user, "c"));
        UUID active = sessionId(sessionService.start(user, "d"));
        jdbc.update("UPDATE user_sessions SET revoked_at = now() - interval '31 days' WHERE id = ?", oldRevoked);
        jdbc.update("UPDATE user_sessions SET expires_at = now() - interval '31 days' WHERE id = ?", oldExpired);
        jdbc.update("UPDATE user_sessions SET revoked_at = now() - interval '1 day' WHERE id = ?", recentlyRevoked);

        sessionService.purgeEnded();

        List<UUID> left = jdbc.queryForList("SELECT id FROM user_sessions WHERE user_id = ?", UUID.class, user.getId());
        assertThat(left).containsExactlyInAnyOrder(recentlyRevoked, active);
    }

    @Test
    void malformedSessionIdIsABadRequest() throws Exception {
        mockMvc.perform(delete("/api/auth/sessions/not-a-uuid").header("Authorization", bearer(createUser())))
                .andExpect(status().isBadRequest());
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

    private UUID sessionId(JwtTokenResponse tokens) {
        return jwtService.extractSessionId(tokens.token());
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
