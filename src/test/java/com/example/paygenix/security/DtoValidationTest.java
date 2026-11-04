package com.example.paygenix.security;

import com.example.paygenix.entity.EditProfile;
import com.example.paygenix.entity.OnboardingFlow;
import com.example.paygenix.entity.User;
import com.example.paygenix.enums.Gender;
import com.example.paygenix.repository.EditProfileRepository;
import com.example.paygenix.repository.OnboardingFlowRepository;
import com.example.paygenix.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// SEC-26: every request body is validated before it reaches a service.
class DtoValidationTest extends IntegrationTest {

    @Autowired EditProfileRepository editProfileRepository;
    @Autowired OnboardingFlowRepository flowRepository;

    @Test
    void verifyWithMissingFieldsIsRejected() throws Exception {
        User user = createUser();
        editProfileRepository.save(EditProfile.builder().email(user.getEmail()).fullName(user.getFullname())
                .phoneNumber("+994501234567").dateOfBirth(LocalDate.of(1990, 1, 1)).gender(Gender.MALE).user(user).build());

        send(post("/api/verify"), bearer(user), "{\"fullName\":\"" + user.getFullname() + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        assertThat(userRepository.findById(user.getId()).orElseThrow().getVerificationStatus())
                .isNotEqualTo(User.VerificationStatus.IDENTITY_VERIFIED);
    }

    @Test
    void profileRejectsBlankNameAndFutureBirthDate() throws Exception {
        String auth = bearer(createUser());

        send(post("/api/profile"), auth, "{\"fullName\":\"   \"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        send(post("/api/profile"), auth, "{\"fullName\":\"" + "a".repeat(101) + "\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        send(post("/api/profile"), auth, "{\"dateOfBirth\":\"01-01-2999\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    // used to reach the database NOT NULL constraints and answer 500
    @Test
    void firstProfileSaveNeedsEveryField() throws Exception {
        send(post("/api/profile"), bearer(createUser()), "{\"phoneNumber\":\"+994501234567\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void onboardingRejectsBlankNameAndBadUsername() throws Exception {
        OnboardingFlow flow = onboardingFlow();
        String auth = "Bearer " + jwtService.generateOnboardingToken(flow);
        String base = "/api/onboarding/" + flow.getId();

        send(put(base + "/full-name"), auth, "{\"fullName\":\"\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        send(put(base + "/full-name"), auth, "{}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        for (String username : new String[]{"ab", "has space", "a".repeat(31), "<script>"}) {
            send(put(base + "/username"), auth, "{\"username\":\"" + username + "\"}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }

        assertThat(flowRepository.findById(flow.getId()).orElseThrow().getStep()).isEqualTo(OnboardingFlow.Step.OTP_VERIFIED);
    }

    @Test
    void loginRejectsOversizedPassword() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@example.test\",\"password\":\"" + "x".repeat(73) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    private ResultActions send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
                               String authorization, String body) throws Exception {
        return mockMvc.perform(request.header("Authorization", authorization)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private OnboardingFlow onboardingFlow() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        OnboardingFlow flow = new OnboardingFlow();
        flow.setId(UUID.randomUUID().toString());
        flow.setEmail("valid-" + suffix + "@example.test");
        flow.setStep(OnboardingFlow.Step.OTP_VERIFIED);
        return flowRepository.save(flow);
    }
}
