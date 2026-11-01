package com.example.paygenix.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// T-094: onboarding entities must not log email or OTP hash; User identity is its id only.
class EntityHygieneTest {

    @Test
    void onboardingToStringHasNoEmailOrOtpHash() {
        OnboardingFlow flow = OnboardingFlow.builder().id("flow-1").email("a@example.test").otpHash("otp-hash").build();
        OnboardingChangePin pin = OnboardingChangePin.builder().id("pin-1").email("a@example.test").otpHash("otp-hash").build();

        assertThat(flow.toString()).contains("flow-1").doesNotContain("a@example.test", "otp-hash");
        assertThat(pin.toString()).contains("pin-1").doesNotContain("a@example.test", "otp-hash");
    }

    @Test
    void usersAreEqualByIdOnly() {
        User a = User.builder().id(7L).email("a@example.test").build();
        User sameIdOtherState = User.builder().id(7L).email("b@example.test").build();

        assertThat(a).isEqualTo(sameIdOtherState).hasSameHashCodeAs(sameIdOtherState);
        assertThat(a).isNotEqualTo(User.builder().id(8L).email("a@example.test").build());
    }

    @Test
    void unsavedUsersAreNotEqual() {
        User a = User.builder().email("a@example.test").build();
        User b = User.builder().email("a@example.test").build();

        assertThat(a).isNotEqualTo(b).isEqualTo(a);
    }
}
