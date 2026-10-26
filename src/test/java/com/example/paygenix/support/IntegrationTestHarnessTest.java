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
    void protectedEndpointWithoutTokenIsNotOk() throws Exception {
        // currently 302 to the login page (SEC-22); tighten to 401 when form login is removed
        int code = mockMvc.perform(get("/api/auth/me")).andReturn().getResponse().getStatus();
        assertThat(code).isNotEqualTo(200);
    }
}
