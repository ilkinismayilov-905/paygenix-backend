package com.example.paygenix.security;

import com.example.paygenix.dto.response.JwtTokenResponse;
import com.example.paygenix.entity.User;
import com.example.paygenix.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// T-077 / SEC-8: a LOCKED account cannot sign in, refresh, or use tokens it already holds.
class LockedAccountTest extends IntegrationTest {

    @Test
    void lockedAccountIsShutOut() throws Exception {
        User user = createUser();
        JwtTokenResponse tokens = sessionService.start(user, "phone");
        lock(user);

        login(user.getEmail(), PASSWORD).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + tokens.token()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/refresh-token").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + tokens.refreshToken() + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void lockIsNotRevealedWithoutThePassword() throws Exception {
        User user = createUser();
        lock(user);

        login(user.getEmail(), "wrong-password").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void activeAccountStillSignsIn() throws Exception {
        login(createUser().getEmail(), PASSWORD).andExpect(status().isOk());
    }

    private void lock(User user) {
        jdbc.update("UPDATE users SET status = 'LOCKED' WHERE id = ?", user.getId());
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }
}
