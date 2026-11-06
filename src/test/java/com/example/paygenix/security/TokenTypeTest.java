package com.example.paygenix.security;

import com.example.paygenix.entity.OnboardingFlow;
import com.example.paygenix.entity.User;
import com.example.paygenix.exceptions.InvalidRefreshTokenException;
import com.example.paygenix.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.HashMap;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

// SEC-3: only access tokens may call business endpoints; onboarding tokens only the onboarding flow.
class TokenTypeTest extends IntegrationTest {

    @Test
    void accessTokenOpensBusinessEndpoint() throws Exception {
        assertThat(businessStatus(bearer(createUser()))).isEqualTo(200);
    }

    @Test
    void onboardingTokenIsRejectedOnBusinessEndpoint() throws Exception {
        assertRejected(businessStatus("Bearer " + onboardingToken()));
    }

    @Test
    void refreshTokenIsRejectedOnBusinessEndpoint() throws Exception {
        User user = createUser();
        assertRejected(businessStatus("Bearer " + jwtService.generateJwtRefreshToken(user, UUID.randomUUID())));
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

    @Test
    void legacyTokenWithoutTokenUseIsRejected() throws Exception {
        User user = createUser();
        String legacy = jwtService.createToken(new HashMap<>(), user.getUsername(), 900L);
        assertRejected(businessStatus("Bearer " + legacy));
    }

    @Test
    void refreshTokenIsRejectedOnOnboardingEndpoint() throws Exception {
        assertRejected(setFullNameStatus("Bearer " + jwtService.generateJwtRefreshToken(createUser(), UUID.randomUUID())));
    }

    @Test
    void accessTokenCannotBeUsedToRefresh() {
        String access = bearer(createUser()).substring("Bearer ".length());
        assertThatThrownBy(() -> sessionService.refresh(access)).isInstanceOf(InvalidRefreshTokenException.class);
    }

    private int businessStatus(String authorization) throws Exception {
        return mockMvc.perform(get("/api/v1/auth/me").header("Authorization", authorization))
                .andReturn().getResponse().getStatus();
    }

    private int setFullNameStatus(String authorization) throws Exception {
        return mockMvc.perform(put("/api/v1/onboarding/" + FLOW_ID + "/full-name")
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Test User\"}"))
                .andReturn().getResponse().getStatus();
    }

    // every onboarding token in this test is issued for this flow, so it matches the path (SEC-19)
    private static final String FLOW_ID = UUID.randomUUID().toString();

    private String onboardingToken() {
        OnboardingFlow flow = new OnboardingFlow();
        flow.setId(FLOW_ID);
        return jwtService.generateOnboardingToken(flow);
    }

    // unauthenticated is still a redirect to the login page (SEC-22); forbidden is 403
    private static void assertRejected(int status) {
        assertThat(status).isIn(302, 401, 403);
    }
}
