package com.example.paygenix.service;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PanServiceTest {

    private final PanService pans = new PanService("unit-test-key-0123456789abcdef0123456789", "400000");

    @RepeatedTest(50)
    void generatedNumbersAreLuhnValidWithTheIssuerBin() {
        String pan = pans.generate();
        assertThat(pan).hasSize(16).startsWith("400000").containsOnlyDigits();
        assertThat(PanService.isLuhnValid(pan)).isTrue();
    }

    @Test
    void luhnKnownVectors() {
        assertThat(PanService.isLuhnValid("4539578763621486")).isTrue();
        assertThat(PanService.isLuhnValid("4539578763621487")).isFalse();
        assertThat(PanService.isLuhnValid("79927398713")).isTrue();
        assertThat(PanService.isLuhnValid("12ab")).isFalse();
    }

    @Test
    void hmacIsDeterministicKeyedAndHidesTheNumber() {
        String pan = "4000001234567899";
        String h = pans.hmac(pan);
        assertThat(h).hasSize(64).isEqualTo(pans.hmac(pan)).doesNotContain(pan);
        PanService otherKey = new PanService("another-test-key-0123456789abcdef01234567", "400000");
        assertThat(otherKey.hmac(pan)).isNotEqualTo(h);
    }

    @Test
    void rejectsWeakKeyAndBadBin() {
        assertThatThrownBy(() -> new PanService("short", "400000")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new PanService("unit-test-key-0123456789abcdef0123456789", "40"))
                .isInstanceOf(IllegalStateException.class);
    }
}
