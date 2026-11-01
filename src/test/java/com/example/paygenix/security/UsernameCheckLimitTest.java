package com.example.paygenix.security;

import com.example.paygenix.entity.OnboardingFlow;
import com.example.paygenix.entity.User;
import com.example.paygenix.repository.OnboardingFlowRepository;
import com.example.paygenix.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// T-092: the username availability check is capped per flow (10) and per IP (30) in 15 minutes.
class UsernameCheckLimitTest extends IntegrationTest {

    @Autowired OnboardingFlowRepository flowRepository;

    @Test
    void eleventhCheckOnOneFlowIsRefused() throws Exception {
        String ip = randomIp();
        String taken = takenUsername();
        OnboardingFlow flow = flowAtUsernameStep();

        for (int i = 0; i < 10; i++) {
            check(flow, taken, ip).andExpect(jsonPath("$.code").value("USERNAME_TAKEN"));
        }
        check(flow, taken, ip)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("USERNAME_CHECK_LIMIT"))
                .andExpect(header().exists("Retry-After"));
        // another flow from the same IP (11 hits, under 30) is not affected
        check(flowAtUsernameStep(), taken, ip).andExpect(jsonPath("$.code").value("USERNAME_TAKEN"));
    }

    @Test
    void thirtyFirstCheckFromOneIpIsRefusedAcrossFlows() throws Exception {
        String ip = randomIp();
        String taken = takenUsername();
        for (int f = 0; f < 3; f++) {
            OnboardingFlow flow = flowAtUsernameStep();
            for (int i = 0; i < 10; i++) {
                check(flow, taken, ip).andExpect(jsonPath("$.code").value("USERNAME_TAKEN"));
            }
        }
        check(flowAtUsernameStep(), taken, ip)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("USERNAME_CHECK_LIMIT"));
    }

    private ResultActions check(OnboardingFlow flow, String username, String ip) throws Exception {
        return mockMvc.perform(put("/api/onboarding/" + flow.getId() + "/username")
                .with(r -> { r.setRemoteAddr(ip); return r; })
                .header("Authorization", "Bearer " + jwtService.generateOnboardingToken(flow))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\"}"));
    }

    private String takenUsername() {
        User owner = createUser();
        String taken = "taken_" + UUID.randomUUID().toString().substring(0, 8);
        owner.setUsername(taken);
        userRepository.save(owner);
        return taken;
    }

    private OnboardingFlow flowAtUsernameStep() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        OnboardingFlow flow = new OnboardingFlow();
        flow.setId(UUID.randomUUID().toString());
        flow.setEmail("flow-" + suffix + "@example.test");
        flow.setFullName("Flow User");
        flow.setStep(OnboardingFlow.Step.FULLNAME_SET);
        flow.setExpiresAt(Instant.now().plusSeconds(600));
        return flowRepository.save(flow);
    }

    private static String randomIp() {
        UUID u = UUID.randomUUID();
        return "10." + (u.getMostSignificantBits() & 0xff) + "." + (u.getLeastSignificantBits() & 0xff) + "." + ((u.getLeastSignificantBits() >> 8) & 0xff);
    }
}
