package com.example.paygenix.security;

import com.example.paygenix.entity.OnboardingFlow;
import com.example.paygenix.entity.User;
import com.example.paygenix.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

// SEC-3: only access tokens may call business endpoints; onboarding tokens only the onboarding flow.
class TokenTypeTest extends IntegrationTest {

    @Test
    void accessTokenOpensBusinessEndpoint() throws Exception {
        assertThat(cardsStatus(bearer(createUser()))).isEqualTo(200);
    }

    @Test
    void onboardingTokenIsRejectedOnBusinessEndpoint() throws Exception {
        assertRejected(cardsStatus("Bearer " + onboardingToken()));
    }

    @Test
    void refreshTokenIsRejectedOnBusinessEndpoint() throws Exception {
        User user = createUser();
        assertRejected(cardsStatus("Bearer " + jwtService.generateJwtRefreshToken(user)));
    }

    @Test
    void onboardingTokenStillPassesAuthorizationOnOnboardingEndpoint() throws Exception {
        int status = setFullNameStatus("Bearer " + onboardingToken());
        assertThat(status).isNotIn(302, 401, 403);
    }

    @Test
    void accessTokenIsRejectedOnOnboardingEndpoint() throws Exception {
        assertRejected(setFullNameStatus(bearer(createUser())));
    }

    private int cardsStatus(String authorization) throws Exception {
        return mockMvc.perform(get("/api/cards").header("Authorization", authorization))
                .andReturn().getResponse().getStatus();
    }

    private int setFullNameStatus(String authorization) throws Exception {
        return mockMvc.perform(put("/api/onboarding/" + UUID.randomUUID() + "/full-name")
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Test User\"}"))
                .andReturn().getResponse().getStatus();
    }

    private String onboardingToken() {
        OnboardingFlow flow = new OnboardingFlow();
        flow.setId(UUID.randomUUID().toString());
        return jwtService.generateOnboardingToken(flow);
    }

    // unauthenticated is still a redirect to the login page (SEC-22); forbidden is 403
    private static void assertRejected(int status) {
        assertThat(status).isIn(302, 401, 403);
    }
}
