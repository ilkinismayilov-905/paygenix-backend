package com.example.paygenix.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// T-093: Spring Security debug logs print the principal, so toString must not carry PII or hashes.
class UserToStringTest {

    @Test
    void toStringHasNoEmailOrHashes() {
        User user = User.builder().id(7L).email("a@example.test").passwordHash("pw-hash").pinHash("pin-hash").build();
        assertThat(user.toString()).contains("7").doesNotContain("a@example.test", "pw-hash", "pin-hash");
    }
}
