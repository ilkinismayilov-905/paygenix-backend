package com.example.paygenix.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// T-142: which client addresses share one login counter
class LoginAttemptKeyTest {

    @Test
    void ipv4IsCountedPerAddress() {
        assertThat(LoginAttemptService.clientKey("203.0.113.7")).isEqualTo("203.0.113.7");
    }

    @Test
    void ipv6IsCountedPerSlash64() {
        assertThat(LoginAttemptService.clientKey("2001:db8:1:2::1"))
                .isEqualTo(LoginAttemptService.clientKey("2001:0db8:0001:0002:ffff:ffff:ffff:ffff"))
                .isEqualTo("20010db800010002/64")
                .isNotEqualTo(LoginAttemptService.clientKey("2001:db8:1:3::1"));
    }

    @Test
    void ipv4MappedIpv6IsTheIpv4Address() {
        assertThat(LoginAttemptService.clientKey("::ffff:203.0.113.7")).isEqualTo("203.0.113.7");
    }

    @Test
    void missingOrOddAddressesStillGetAKey() {
        assertThat(LoginAttemptService.clientKey(null)).isEqualTo("unknown");
        assertThat(LoginAttemptService.clientKey("")).isEqualTo("unknown");
        assertThat(LoginAttemptService.clientKey("not:an:address::zz")).isEqualTo("not:an:address::zz");
    }
}
