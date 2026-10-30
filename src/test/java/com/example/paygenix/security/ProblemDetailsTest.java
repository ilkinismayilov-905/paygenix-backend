package com.example.paygenix.security;

import com.example.paygenix.entity.Story;
import com.example.paygenix.entity.User;
import com.example.paygenix.enums.MediaType;
import com.example.paygenix.enums.Role;
import com.example.paygenix.enums.StoryVisibility;
import com.example.paygenix.repository.StoryRepository;
import com.example.paygenix.service.EmailService;
import com.example.paygenix.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// SEC-23, FLOW-9, CQ-1, T-03A: RFC 9457 bodies with stable codes, no 500s for client mistakes, no email enumeration.
class ProblemDetailsTest extends IntegrationTest {

    @MockitoBean EmailService emailService;
    @Autowired StoryRepository storyRepository;

    @Test
    void validationErrorIsAProblemDocument() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.detail").exists());
    }

    @Test
    void unauthenticatedIsAProblemDocument() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void missingRoleIsAProblemDocument() throws Exception {
        mockMvc.perform(get("/api/user").header("Authorization", bearer(createUser())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void unknownPathIs404NotAHandlerFailure() throws Exception {
        mockMvc.perform(get("/api/does-not-exist").header("Authorization", bearer(createUser())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void wrongMethodIs405() throws Exception {
        mockMvc.perform(put("/api/auth/login").contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void registeredAndNewEmailsGetTheSameOnboardingAnswer() throws Exception {
        User existing = createUser();
        String fresh = "fresh-" + UUID.randomUUID() + "@example.test";

        submitEmail(existing.getEmail()).andExpect(status().isOk()).andExpect(jsonPath("$.data").isString());
        submitEmail(fresh).andExpect(status().isOk()).andExpect(jsonPath("$.data").isString());

        verify(emailService, never()).sendOtp(eq(existing.getEmail()), anyString());
        verify(emailService).sendOtp(eq(fresh), anyString());
    }

    @Test
    void onboardingMistakesAreClientErrors() throws Exception {
        String body = submitEmail("otp-" + UUID.randomUUID() + "@example.test").andReturn().getResponse().getContentAsString();
        String flowId = JsonPath.read(body, "$.data");

        otp(flowId, "000000").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_OTP"));
        otp(UUID.randomUUID().toString(), "000000").andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("FLOW_NOT_FOUND"));
    }

    @Test
    void deletingSomeoneElsesStoryIs403AndMissingIs404() throws Exception {
        User admin = createUser(Role.ADMIN_ROLE);
        Long storyId = storyRepository.save(Story.builder()
                .user(admin)
                .objectName("test/" + System.nanoTime())
                .mediaType(MediaType.IMAGE)
                .visibility(StoryVisibility.PUBLIC)
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .deleted(false)
                .build()).getId();
        User otherAdmin = createUser(Role.ADMIN_ROLE);

        int status = mockMvc.perform(delete("/api/stories/" + storyId).header("Authorization", bearer(otherAdmin)))
                .andReturn().getResponse().getStatus();
        assertThat(status).isEqualTo(403);
        mockMvc.perform(delete("/api/stories/999999999").header("Authorization", bearer(otherAdmin)))
                .andExpect(status().isNotFound());
    }

    private ResultActions submitEmail(String email) throws Exception {
        return mockMvc.perform(post("/api/onboarding/email").contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\"}"));
    }

    private ResultActions otp(String flowId, String otp) throws Exception {
        return mockMvc.perform(post("/api/onboarding/" + flowId + "/otp").contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"otp\":\"" + otp + "\"}"));
    }
}
