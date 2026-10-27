package com.example.paygenix.security;

import com.example.paygenix.support.IntegrationTest;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// SEC-27: controllers asked for a Jwt principal, but JwtFilter sets a User, so the argument was null (NPE → 500).
class AuthenticationPrincipalTest extends IntegrationTest {

    @Test
    void storyFeedResolvesTheLoggedInUser() throws Exception {
        mockMvc.perform(get("/api/stories/feed").header("Authorization", bearer(createUser())))
                .andExpect(status().isOk());
    }
}
