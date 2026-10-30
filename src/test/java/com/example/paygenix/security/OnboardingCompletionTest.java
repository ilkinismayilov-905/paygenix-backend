package com.example.paygenix.security;

import com.example.paygenix.dto.request.LoginRequest;
import com.example.paygenix.entity.OnboardingFlow;
import com.example.paygenix.repository.OnboardingFlowRepository;
import com.example.paygenix.service.AuthService;
import com.example.paygenix.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// T-038: setting the PIN completes onboarding without issuing a token; the user then logs in normally.
class OnboardingCompletionTest extends IntegrationTest {

    @Autowired OnboardingFlowRepository flowRepository;
    @Autowired AuthService authService;

    @Test
    void setPinCreatesUserWhoCanLogIn() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        OnboardingFlow flow = new OnboardingFlow();
        flow.setId(UUID.randomUUID().toString());
        flow.setEmail("onboard-" + suffix + "@example.test");
        flow.setFullName("Onboard User");
        flow.setUsernameCandidate("onboard_" + suffix);
        flow.setStep(OnboardingFlow.Step.USERNAME_SET);
        flowRepository.save(flow);

        mockMvc.perform(put("/api/onboarding/" + flow.getId() + "/pin")
                        .header("Authorization", "Bearer " + jwtService.generateOnboardingToken(flow))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pin\":\"" + PIN + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());

        assertThat(authService.login(new LoginRequest(flow.getEmail(), PIN)).token()).isNotBlank();
    }
}
