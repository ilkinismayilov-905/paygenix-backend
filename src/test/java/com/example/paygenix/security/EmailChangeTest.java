package com.example.paygenix.security;

import com.example.paygenix.dto.request.EditProfileRequest;
import com.example.paygenix.dto.response.JwtTokenResponse;
import com.example.paygenix.entity.User;
import com.example.paygenix.service.EmailService;
import com.example.paygenix.service.OtpGenerator;
import com.example.paygenix.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;

import java.lang.reflect.RecordComponent;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// SEC-14: an email changes only after the new address confirms a code. SEC-17: OTPs are 6 digits from a CSPRNG.
class EmailChangeTest extends IntegrationTest {

    @MockitoBean EmailService emailService;
    @Autowired StringRedisTemplate redis;

    // shortcut: asserted on the DTO because POST /api/profile still 500s on the Redis cache (T-016); go through HTTP once fixed
    @Test
    void profileEditCannotCarryAnEmail() {
        assertThat(EditProfileRequest.class.getRecordComponents())
                .extracting(RecordComponent::getName)
                .doesNotContain("email");
    }

    @Test
    void confirmedCodeChangesTheEmailAndSignsOutEverywhere() throws Exception {
        User user = createUser();
        JwtTokenResponse session = sessionService.start(user, "phone");
        String newEmail = "new-" + UUID.randomUUID() + "@example.test";

        start(session.token(), newEmail).andExpect(status().isOk());
        confirm(session.token(), sentCode(newEmail)).andExpect(status().isOk());

        assertThat(userRepository.findById(user.getId()).orElseThrow().getEmail()).isEqualTo(newEmail);
        verify(emailService).sendEmailChanged(user.getEmail());
        int me = mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + session.token()))
                .andReturn().getResponse().getStatus();
        assertThat(me).isIn(302, 401);
    }

    @Test
    void startNeedsTheCurrentPin() throws Exception {
        User user = createUser();
        String token = bearer(user).substring(7);
        String newEmail = "new-" + UUID.randomUUID() + "@example.test";

        start(token, newEmail, "000000").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INVALID_PIN"));
        verify(emailService, never()).sendOtp(any(), any());
        // a wrong PIN does not burn the one-per-minute code cooldown
        start(token, newEmail).andExpect(status().isOk());
    }

    @Test
    void accountWithoutPinCannotStart() throws Exception {
        User user = createUser();
        user.setPinHash(null);
        userRepository.save(user);

        start(bearer(user).substring(7), "new-" + UUID.randomUUID() + "@example.test")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PIN_NOT_SET"));
    }

    @Test
    void mailOutageAfterConfirmKeepsTheNewEmail() throws Exception {
        User user = createUser();
        String token = bearer(user).substring(7);
        String newEmail = "new-" + UUID.randomUUID() + "@example.test";
        doThrow(new MailSendException("down")).when(emailService).sendEmailChanged(any());

        start(token, newEmail).andExpect(status().isOk());
        confirm(token, sentCode(newEmail)).andExpect(status().isOk());

        assertThat(userRepository.findById(user.getId()).orElseThrow().getEmail()).isEqualTo(newEmail);
        int me = mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getStatus();
        assertThat(me).isIn(302, 401);
    }

    @Test
    void wrongPinsLockThePinLikePaymentsDo() throws Exception {
        String token = bearer(createUser()).substring(7);
        String newEmail = "new-" + UUID.randomUUID() + "@example.test";
        for (int i = 0; i < 4; i++) {
            start(token, newEmail, "000000").andExpect(status().isForbidden());
        }
        start(token, newEmail, "000000").andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("PIN_LOCKED"))
                .andExpect(header().exists("Retry-After"));
        start(token, newEmail).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("PIN_LOCKED"));
    }

    @Test
    void wrongCodeDoesNotChangeTheEmailAndTriesAreLimited() throws Exception {
        User user = createUser();
        String token = bearer(user).substring(7);
        String newEmail = "new-" + UUID.randomUUID() + "@example.test";
        start(token, newEmail).andExpect(status().isOk());
        String code = sentCode(newEmail);
        String wrong = code.equals("000000") ? "000001" : "000000";

        for (int i = 0; i < 5; i++) {
            confirm(token, wrong).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_OTP"));
        }
        confirm(token, code).andExpect(status().isTooManyRequests()) // the pending change is gone after 5 tries
                .andExpect(jsonPath("$.code").value("TOO_MANY_ATTEMPTS"));
        confirm(token, code).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("OTP_EXPIRED"));

        assertThat(userRepository.findById(user.getId()).orElseThrow().getEmail()).isEqualTo(user.getEmail());
    }

    @Test
    void cannotClaimAnEmailThatIsInUse() throws Exception {
        User other = createUser();
        start(bearer(createUser()).substring(7), other.getEmail()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
    }

    @Test
    void probingTakenEmailsIsRateLimited() throws Exception {
        String token = bearer(createUser()).substring(7);
        start(token, createUser().getEmail()).andExpect(status().isConflict());
        // the second probe within a minute hits the cooldown before the taken check, so it reveals nothing
        start(token, createUser().getEmail()).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("OTP_SEND_TOO_FREQUENT"));
    }

    @Test
    void dailyLimitHidesWhetherAnEmailIsTaken() throws Exception {
        User user = createUser();
        redis.opsForValue().set("email-change:daily:" + user.getId(), "5", Duration.ofHours(1));
        start(bearer(user).substring(7), createUser().getEmail()).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("EMAIL_CHANGE_LIMIT"));
    }

    @Test
    void dailyCounterWithoutTtlGetsOneBack() throws Exception {
        User user = createUser();
        String key = "email-change:daily:" + user.getId();
        redis.opsForValue().set(key, "2"); // lost its EXPIRE: would block the user forever once it reaches the limit

        start(bearer(user).substring(7), "ttl-" + UUID.randomUUID() + "@example.test").andExpect(status().isOk());

        assertThat(redis.opsForValue().get(key)).isEqualTo("3");
        assertThat(redis.getExpire(key)).isBetween(86_000L, 86_400L);
    }

    @Test
    void codesCannotBeRequestedBackToBack() throws Exception {
        String token = bearer(createUser()).substring(7);
        start(token, "a-" + UUID.randomUUID() + "@example.test").andExpect(status().isOk());
        start(token, "b-" + UUID.randomUUID() + "@example.test").andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("OTP_SEND_TOO_FREQUENT"))
                .andExpect(header().string("Retry-After", "60"));
    }

    @Test
    void sixthChangeRequestInADayIsLimitedUntilTheCounterExpires() throws Exception {
        User user = createUser();
        // five starts already used today, counter expires in an hour
        redis.opsForValue().set("email-change:daily:" + user.getId(), "5", Duration.ofHours(1));

        start(bearer(user).substring(7), "limit-" + UUID.randomUUID() + "@example.test")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("EMAIL_CHANGE_LIMIT"))
                .andExpect(header().string("Retry-After", matchesPattern("3[56]\\d\\d")));
    }

    @Test
    void secondAccountConfirmingTheSameEmailIsRefused() throws Exception {
        String newEmail = "contested-" + UUID.randomUUID() + "@example.test";
        String first = bearer(createUser()).substring(7);
        String second = bearer(createUser()).substring(7);
        start(first, newEmail).andExpect(status().isOk());
        start(second, newEmail).andExpect(status().isOk());
        ArgumentCaptor<String> codes = ArgumentCaptor.forClass(String.class);
        verify(emailService, times(2)).sendOtp(eq(newEmail), codes.capture());

        confirm(first, codes.getAllValues().get(0)).andExpect(status().isOk());
        confirm(second, codes.getAllValues().get(1)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
    }

    @Test
    void googleAccountKeepsUsernameAndEmailInStep() throws Exception {
        User user = createUser();
        user.setProvider(User.AuthProvider.GOOGLE);
        user.setUsername(user.getEmail());
        userRepository.save(user);
        String token = bearer(user).substring(7);
        String newEmail = "g-" + UUID.randomUUID() + "@example.test";

        start(token, newEmail).andExpect(status().isOk());
        confirm(token, sentCode(newEmail)).andExpect(status().isOk());

        User changed = userRepository.findById(user.getId()).orElseThrow();
        assertThat(changed.getEmail()).isEqualTo(newEmail);
        assertThat(changed.getUsername()).isEqualTo(newEmail);
    }

    @Test
    void otpIsSixDigitsAndVaries() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            String otp = OtpGenerator.newOtp();
            assertThat(otp).matches("\\d{6}");
            seen.add(otp);
        }
        assertThat(seen).hasSizeGreaterThan(190);
    }

    private String sentCode(String to) {
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendOtp(eq(to), code.capture());
        return code.getValue();
    }

    private ResultActions start(String token, String email) throws Exception {
        return start(token, email, PIN);
    }

    private ResultActions start(String token, String email, String pin) throws Exception {
        return mockMvc.perform(post("/api/profile/email").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"pin\":\"" + pin + "\"}"));
    }

    private ResultActions confirm(String token, String otp) throws Exception {
        return mockMvc.perform(post("/api/profile/email/confirm").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"otp\":\"" + otp + "\"}"));
    }
}
