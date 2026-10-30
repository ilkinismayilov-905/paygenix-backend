package com.example.paygenix.support;

import com.example.paygenix.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IntegrationTestHarnessTest extends IntegrationTest {

    @Value("${jwt.secret}") String jwtSecret;

    @Test
    void usesTestProfileNotLocalEnv() {
        assertThat(jwtSecret).startsWith("test-only-jwt-secret");
    }

    @Test
    void issuedTokenOpensProtectedEndpoint() throws Exception {
        User user = createUser();

        mockMvc.perform(get("/api/auth/me").header("Authorization", bearer(user)))
                .andExpect(status().isOk());
    }

    @Test
    void protectedEndpointWithoutTokenIsRejected() throws Exception {
        mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }
}
