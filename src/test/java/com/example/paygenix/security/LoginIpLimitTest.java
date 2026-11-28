package com.example.paygenix.security;

import com.example.paygenix.entity.User;
import com.example.paygenix.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// T-142: at most 30 login attempts per client address (IPv6: per /64) in 15 minutes, across all emails.
@TestPropertySource(properties = "LOGIN_IP_MAX_ATTEMPTS=30")
class LoginIpLimitTest extends IntegrationTest {

    private static final AtomicInteger NEXT = new AtomicInteger(1);

    @Test
    void thirtyFirstAttemptFromOneAddressIsRefused() throws Exception {
        String ip = freshIpv4();
        for (int i = 0; i < 30; i++) {
            login(randomEmail(), "wrong-password", ip).andExpect(status().isUnauthorized());
        }
        User user = createUser();
        login(user.getEmail(), PASSWORD, ip)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("LOGIN_RATE_LIMITED"))
                .andExpect(header().string("Retry-After", org.hamcrest.Matchers.matchesPattern("[1-9][0-9]{0,2}")));
        // another address is not affected
        login(user.getEmail(), PASSWORD, freshIpv4()).andExpect(status().isOk());
    }

    @Test
    void successfulLoginsGiveTheirAttemptBack() throws Exception {
        String ip = freshIpv4();
        for (int i = 0; i < 29; i++) {
            login(randomEmail(), "wrong-password", ip).andExpect(status().isUnauthorized());
        }
        User user = createUser();
        for (int i = 0; i < 5; i++) {
            login(user.getEmail(), PASSWORD, ip).andExpect(status().isOk());
        }
        login(randomEmail(), "wrong-password", ip).andExpect(status().isUnauthorized()); // the 30th
        login(randomEmail(), "wrong-password", ip).andExpect(status().isTooManyRequests());
    }

    @Test
    void ipv6CountsPerSlash64() throws Exception {
        int block = NEXT.getAndIncrement();
        for (int i = 1; i <= 30; i++) {
            login(randomEmail(), "wrong-password", "2001:db8:" + Integer.toHexString(block) + ":1::" + Integer.toHexString(i))
                    .andExpect(status().isUnauthorized());
        }
        login(randomEmail(), "wrong-password", "2001:db8:" + Integer.toHexString(block) + ":1:ffff:ffff:ffff:ffff")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("LOGIN_RATE_LIMITED"));
        login(randomEmail(), "wrong-password", "2001:db8:" + Integer.toHexString(block) + ":2::1")
                .andExpect(status().isUnauthorized());
    }

    // a refused address does not use up the email's five attempts, so it cannot lock accounts once it is blocked
    @Test
    void blockedAddressDoesNotCountAgainstTheEmail() throws Exception {
        String ip = freshIpv4();
        for (int i = 0; i < 30; i++) {
            login(randomEmail(), "wrong-password", ip);
        }
        User user = createUser();
        for (int i = 0; i < 10; i++) {
            login(user.getEmail(), "wrong-password", ip).andExpect(status().isTooManyRequests());
        }
        String other = freshIpv4();
        for (int i = 0; i < 4; i++) {
            login(user.getEmail(), "wrong-password", other).andExpect(status().isUnauthorized());
        }
        login(user.getEmail(), PASSWORD, other).andExpect(status().isOk());
    }

    @Test
    void parallelBurstFromOneAddressGetsAtMostThirtyTries() throws Exception {
        String ip = freshIpv4();
        ExecutorService pool = Executors.newFixedThreadPool(16);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < 60; i++) {
                results.add(pool.submit(() -> login(randomEmail(), "wrong-password", ip).andReturn().getResponse().getStatus()));
            }
            long checked = 0;
            for (Future<Integer> result : results) {
                if (result.get() == 401) checked++;
            }
            assertThat(checked).isEqualTo(30);
        } finally {
            pool.shutdown();
        }
    }

    private ResultActions login(String email, String password, String ip) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                })
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }

    private static String randomEmail() {
        return UUID.randomUUID() + "@example.test";
    }

    // TEST-NET-3, one address per test (and per run of the JVM, since Redis lives as long as the containers)
    private static String freshIpv4() {
        long n = (System.nanoTime() / 1000 + NEXT.getAndIncrement()) & 0xffff;
        return "203.0." + (n >> 8 & 0xff) + "." + (n & 0xff);
    }
}
