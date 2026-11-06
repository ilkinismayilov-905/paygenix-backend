package com.example.paygenix.security;

import com.example.paygenix.entity.User;
import com.example.paygenix.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// SEC-8: five login attempts per email per 15 minutes; then even the right password is refused.
class LoginAttemptLimitTest extends IntegrationTest {

    @Test
    void sixthAttemptIsLockedEvenWithTheRightPassword() throws Exception {
        User user = createUser();
        for (int i = 0; i < 5; i++) {
            login(user.getEmail(), "wrong-password").andExpect(status().isUnauthorized());
        }
        login(user.getEmail(), PASSWORD).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("LOGIN_LOCKED"))
                .andExpect(header().string("Retry-After", "900"));
        login(user.getEmail().toUpperCase(), PASSWORD).andExpect(status().isTooManyRequests());
    }

    @Test
    void successfulLoginResetsTheCounter() throws Exception {
        User user = createUser();
        for (int i = 0; i < 4; i++) {
            login(user.getEmail(), "wrong-password").andExpect(status().isUnauthorized());
        }
        login(user.getEmail(), PASSWORD).andExpect(status().isOk());
        for (int i = 0; i < 5; i++) {
            login(user.getEmail(), "wrong-password").andExpect(status().isUnauthorized());
        }
    }

    // same answer for unknown emails, so the lock does not reveal which accounts exist
    @Test
    void unknownEmailIsLockedTheSameWay() throws Exception {
        String email = UUID.randomUUID() + "@example.test";
        for (int i = 0; i < 5; i++) {
            login(email, "wrong-password").andExpect(status().isUnauthorized());
        }
        login(email, "wrong-password").andExpect(status().isTooManyRequests());
    }

    @Test
    void lockIsPerEmail() throws Exception {
        User locked = createUser();
        for (int i = 0; i < 5; i++) {
            login(locked.getEmail(), "wrong-password");
        }
        login(locked.getEmail(), PASSWORD).andExpect(status().isTooManyRequests());
        login(createUser().getEmail(), PASSWORD).andExpect(status().isOk());
    }

    // the attempt is counted before the slow password check, so a burst cannot get past the limit
    @Test
    void parallelGuessesGetAtMostFiveTries() throws Exception {
        User user = createUser();
        ExecutorService pool = Executors.newFixedThreadPool(10);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                results.add(pool.submit(() -> login(user.getEmail(), "wrong-password").andReturn().getResponse().getStatus()));
            }
            long checked = 0;
            for (Future<Integer> result : results) {
                if (result.get() == 401) checked++;
            }
            assertThat(checked).isEqualTo(5);
        } finally {
            pool.shutdown();
        }
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }
}
