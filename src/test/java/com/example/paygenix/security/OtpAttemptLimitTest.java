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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// T-138: after 5 wrong codes the flow stays locked; the counter must not start over at 1.
class OtpAttemptLimitTest extends IntegrationTest {

    private static final String CODE = "246810";

    @Autowired OnboardingFlowRepository flowRepository;
    @Autowired OnboardingChangePinRepository changePinRepository;

    @Test
    void signUpCodeStaysLockedAfterFiveWrongTries() throws Exception {
        OnboardingFlow flow = new OnboardingFlow();
        flow.setId(UUID.randomUUID().toString());
        flow.setEmail("otp-" + flow.getId().substring(0, 8) + "@example.test");
        flow.setStep(OnboardingFlow.Step.EMAIL_SUBMITTED);
        flow.setOtpHash(passwordEncoder.encode(CODE));
        flow.setOtpExpiresAt(Instant.now().plusSeconds(300));
        flow.setExpiresAt(Instant.now().plusSeconds(1800));
        flowRepository.save(flow);

        assertLockedAfterFiveWrong(otp -> post("/api/onboarding/" + flow.getId() + "/otp").content(otp));
    }

    @Test
    void pinChangeCodeStaysLockedAfterFiveWrongTries() throws Exception {
        User owner = createUser();
        OnboardingChangePin flow = changePinRepository.save(OnboardingChangePin.builder()
                .id(UUID.randomUUID().toString())
                .email(owner.getEmail())
                .step(OnboardingChangePin.Step.OTP_SENT)
                .otpHash(passwordEncoder.encode(CODE))
                .otpExpiresAt(Instant.now().plusSeconds(300))
                .build());

        assertLockedAfterFiveWrong(otp -> post("/api/change-pin/" + flow.getId() + "/otp")
                .header("Authorization", bearer(owner)).content(otp));
    }

    private interface Request { MockHttpServletRequestBuilder of(String body) throws Exception; }

    private void assertLockedAfterFiveWrong(Request request) throws Exception {
        for (int i = 1; i < 5; i++) {
            send(request, "000000").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_OTP"));
        }
        send(request, "000000").andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("TOO_MANY_ATTEMPTS"));
        // the right code no longer works, and more wrong ones do not get fresh tries
        send(request, CODE).andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("TOO_MANY_ATTEMPTS"));
        send(request, "000000").andExpect(status().isTooManyRequests());
    }

    private ResultActions send(Request request, String otp) throws Exception {
        return mockMvc.perform(request.of("{\"otp\":\"" + otp + "\"}").contentType(MediaType.APPLICATION_JSON));
    }
}
