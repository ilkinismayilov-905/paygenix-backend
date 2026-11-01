package com.example.paygenix.security;

import com.example.paygenix.dto.request.LoginRequest;
import com.example.paygenix.entity.OnboardingFlow;
import com.example.paygenix.repository.OnboardingFlowRepository;
import com.example.paygenix.service.AuthService;
import com.example.paygenix.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

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
        OnboardingFlow flow = newFlow();

        setPin(flow).andExpect(status().isOk()).andExpect(jsonPath("$.data").doesNotExist());
        assertThat(flowRepository.findById(flow.getId()).orElseThrow().getStep())
                .isEqualTo(OnboardingFlow.Step.COMPLETED);

        // a finished flow cannot create a second user
        setPin(flow).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_STEP"));

        assertThat(authService.login(new LoginRequest(flow.getEmail(), PASSWORD), "test").token()).isNotBlank();
    }

    // 40 chars but 80 UTF-8 bytes: bcrypt rejects more than 72 bytes, so this must be a 400, not a 500
    @Test
    void passwordOverBcryptByteLimitIsRejected() throws Exception {
        OnboardingFlow flow = newFlow();

        setPin(flow, "ə".repeat(40))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        assertThat(flowRepository.findById(flow.getId()).orElseThrow().getStep())
                .isEqualTo(OnboardingFlow.Step.USERNAME_SET);
    }

    private OnboardingFlow newFlow() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        OnboardingFlow flow = new OnboardingFlow();
        flow.setId(UUID.randomUUID().toString());
        flow.setEmail("onboard-" + suffix + "@example.test");
        flow.setFullName("Onboard User");
        flow.setUsernameCandidate("onboard_" + suffix);
        flow.setStep(OnboardingFlow.Step.USERNAME_SET);
        return flowRepository.save(flow);
    }

    private ResultActions setPin(OnboardingFlow flow) throws Exception {
        return setPin(flow, PASSWORD);
    }

    private ResultActions setPin(OnboardingFlow flow, String password) throws Exception {
        return mockMvc.perform(put("/api/onboarding/" + flow.getId() + "/pin")
                .header("Authorization", "Bearer " + jwtService.generateOnboardingToken(flow))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pin\":\"" + PIN + "\",\"password\":\"" + password + "\"}"));
    }
}
