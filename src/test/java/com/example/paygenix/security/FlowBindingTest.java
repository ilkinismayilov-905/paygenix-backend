package com.example.paygenix.security;

import com.example.paygenix.entity.OnboardingChangePin;
import com.example.paygenix.entity.OnboardingFlow;
import com.example.paygenix.entity.User;
import com.example.paygenix.repository.OnboardingChangePinRepository;
import com.example.paygenix.repository.OnboardingFlowRepository;
import com.example.paygenix.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// SEC-19: an onboarding token drives only its own flow. SEC-20: a PIN-change flow belongs to its user and expires.
class FlowBindingTest extends IntegrationTest {

    @Autowired OnboardingFlowRepository flowRepository;
    @Autowired OnboardingChangePinRepository changePinRepository;

    @Test
    void onboardingTokenCannotDriveAnotherFlow() throws Exception {
        OnboardingFlow mine = onboardingFlow();
        OnboardingFlow victims = onboardingFlow();

        int status = setOnboardingPin(victims, jwtService.generateOnboardingToken(mine)).andReturn().getResponse().getStatus();

        assertThat(status).isIn(302, 401, 403);
        assertThat(flowRepository.findById(victims.getId()).orElseThrow().getStep()).isEqualTo(OnboardingFlow.Step.USERNAME_SET);
        assertThat(userRepository.existsByEmail(victims.getEmail())).isFalse();
    }

    @Test
    void onboardingTokenStillDrivesItsOwnFlow() throws Exception {
        OnboardingFlow flow = onboardingFlow();
        setOnboardingPin(flow, jwtService.generateOnboardingToken(flow)).andExpect(status().isOk());
    }

    @Test
    void onboardingCannotCreateASecondAccountForATakenEmail() throws Exception {
        OnboardingFlow flow = onboardingFlow();
        User existing = createUser();
        flow.setEmail(existing.getEmail());
        flowRepository.save(flow);

        setOnboardingPin(flow, jwtService.generateOnboardingToken(flow)).andExpect(status().isBadRequest());
        assertThat(flowRepository.findById(flow.getId()).orElseThrow().getStep()).isEqualTo(OnboardingFlow.Step.USERNAME_SET);
    }

    @Test
    void ownerCanFinishTheirPinChange() throws Exception {
        User owner = createUser();
        OnboardingChangePin flow = verifiedChangePinFlow(owner);

        changePin(flow, owner).andExpect(status().isOk());
        assertThat(passwordEncoder.matches("135790", userRepository.findById(owner.getId()).orElseThrow().getPinHash())).isTrue();
    }

    @Test
    void anotherUserCannotFinishSomeoneElsesPinChange() throws Exception {
        User owner = createUser();
        OnboardingChangePin flow = verifiedChangePinFlow(owner);

        changePin(flow, createUser()).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/change-pin/" + flow.getId() + "/otp").header("Authorization", bearer(createUser()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"otp\":\"123456\"}"))
                .andExpect(status().isNotFound());
        assertThat(passwordEncoder.matches(PIN, userRepository.findById(owner.getId()).orElseThrow().getPinHash())).isTrue();
    }

    @Test
    void pinChangeFlowExpires() throws Exception {
        User owner = createUser();
        OnboardingChangePin flow = verifiedChangePinFlow(owner);
        jdbc.update("UPDATE onboarding_change_pin SET created_at = now() - interval '16 minutes' WHERE id = ?", flow.getId());

        assertThat(changePin(flow, owner).andReturn().getResponse().getStatus()).isGreaterThanOrEqualTo(400);
        assertThat(passwordEncoder.matches(PIN, userRepository.findById(owner.getId()).orElseThrow().getPinHash())).isTrue();
    }

    private OnboardingFlow onboardingFlow() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        OnboardingFlow flow = new OnboardingFlow();
        flow.setId(UUID.randomUUID().toString());
        flow.setEmail("flow-" + suffix + "@example.test");
        flow.setFullName("Flow User");
        flow.setUsernameCandidate("flow_" + suffix);
        flow.setStep(OnboardingFlow.Step.USERNAME_SET);
        return flowRepository.save(flow);
    }

    private OnboardingChangePin verifiedChangePinFlow(User owner) {
        return changePinRepository.save(OnboardingChangePin.builder()
                .id(UUID.randomUUID().toString())
                .email(owner.getEmail())
                .step(OnboardingChangePin.Step.OTP_VERIFIED)
                .build());
    }

    private ResultActions setOnboardingPin(OnboardingFlow flow, String token) throws Exception {
        return mockMvc.perform(put("/api/onboarding/" + flow.getId() + "/pin")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pin\":\"" + PIN + "\",\"password\":\"" + PASSWORD + "\"}"));
    }

    private ResultActions changePin(OnboardingChangePin flow, User caller) throws Exception {
        return mockMvc.perform(post("/api/change-pin/" + flow.getId() + "/pin").header("Authorization", bearer(caller))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"newPin\":\"135790\",\"reEnterNewPin\":\"135790\"}"));
    }
}
