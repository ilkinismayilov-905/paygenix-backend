package com.example.paygenix.security;

import com.example.paygenix.dto.request.TransferRequest;
import com.example.paygenix.entity.User;
import com.example.paygenix.exceptions.PinRejectedException;
import com.example.paygenix.service.TransferService;
import com.example.paygenix.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// FLOW-7: five wrong PINs lock PIN checks; while locked even the right PIN is refused and no money moves.
class PinAttemptLimitTest extends IntegrationTest {

    @Autowired TransferService transferService;
    @Autowired StringRedisTemplate redis;

    @Test
    void fifthWrongPinLocksAndTheRightPinIsRefusedWhileLocked() throws Exception {
        User sender = createUser();
        String from = issueCard(sender, "AZN");
        String to = issueCard(createUser(), "AZN");
        setBalance(from, "100.00");

        for (int i = 1; i <= 4; i++) {
            transfer(sender, from, to, "000000").andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("INVALID_PIN"));
        }
        transfer(sender, from, to, "000000").andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("PIN_LOCKED"))
                .andExpect(header().string("Retry-After", "900"));
        transfer(sender, from, to, PIN).andExpect(status().isTooManyRequests());

        assertThat(balanceOf(from)).isEqualByComparingTo("100.00");
    }

    @Test
    void wrongPinCounterAlwaysHasTheLockTtl() throws Exception {
        User sender = createUser();
        String from = issueCard(sender, "AZN");
        String to = issueCard(createUser(), "AZN");
        String key = "pin:failures:" + sender.getId();
        redis.opsForValue().set(key, "3"); // lost its TTL earlier

        transfer(sender, from, to, "000000").andExpect(status().isForbidden());

        assertThat(redis.opsForValue().get(key)).isEqualTo("4");
        assertThat(redis.getExpire(key)).isBetween(890L, 900L);
    }

    @Test
    void laterLockedHitsReportTheTimeLeft() throws Exception {
        User sender = createUser();
        String from = issueCard(sender, "AZN");
        String to = issueCard(createUser(), "AZN");
        String key = "pin:failures:" + sender.getId();
        redis.opsForValue().set(key, "5", Duration.ofSeconds(120)); // locked 13 minutes ago

        transfer(sender, from, to, PIN).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("PIN_LOCKED"))
                .andExpect(header().string("Retry-After", matchesPattern("1[01]\\d|120")));
    }

    @Test
    void lockedCounterWithoutTtlIsHealed() throws Exception {
        User sender = createUser();
        String from = issueCard(sender, "AZN");
        String to = issueCard(createUser(), "AZN");
        String key = "pin:failures:" + sender.getId();
        redis.opsForValue().set(key, "5"); // lost its TTL before the atomic script

        transfer(sender, from, to, PIN).andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "900"));
        assertThat(redis.getExpire(key)).isBetween(890L, 900L);
    }

    @Test
    void rightPinResetsTheCounter() throws Exception {
        User sender = createUser();
        String from = issueCard(sender, "AZN");
        String to = issueCard(createUser(), "AZN");
        setBalance(from, "100.00");

        for (int i = 0; i < 4; i++) {
            transfer(sender, from, to, "000000").andExpect(status().isForbidden());
        }
        transfer(sender, from, to, PIN).andExpect(status().isOk());
        transfer(sender, from, to, "000000").andExpect(status().isForbidden()); // counter starts over

        assertThat(balanceOf(from)).isEqualByComparingTo("89.80");
    }

    @Test
    void lockIsPerUser() throws Exception {
        User locked = createUser();
        String lockedCard = issueCard(locked, "AZN");
        String to = issueCard(createUser(), "AZN");
        for (int i = 0; i < 5; i++) {
            transfer(locked, lockedCard, to, "000000");
        }

        User other = createUser();
        String otherCard = issueCard(other, "AZN");
        setBalance(otherCard, "100.00");
        transfer(other, otherCard, to, PIN).andExpect(status().isOk());
    }

    @Test
    void parallelGuessesCannotExceedTheLimit() throws Exception {
        User sender = createUser();
        String from = issueCard(sender, "AZN");
        String to = issueCard(createUser(), "AZN");
        setBalance(from, "100.00");
        TransferRequest guess = new TransferRequest(from, to, new BigDecimal("10.00"), "000000");

        int threads = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    try {
                        transferService.transfer(sender, guess);
                        return "OK";
                    } catch (PinRejectedException rejected) {
                        return rejected.getErrorCode();
                    }
                }));
            }
            start.countDown();
            List<String> codes = new ArrayList<>();
            for (Future<String> f : futures) {
                codes.add(f.get(60, TimeUnit.SECONDS));
            }
            // only 5 guesses are ever checked: 4 answer INVALID_PIN, the 5th and every later one PIN_LOCKED
            assertThat(codes).filteredOn("INVALID_PIN"::equals).hasSize(4);
            assertThat(codes).filteredOn("PIN_LOCKED"::equals).hasSize(16);
        } finally {
            pool.shutdownNow();
        }
        assertThat(balanceOf(from)).isEqualByComparingTo("100.00");
    }

    private ResultActions transfer(User caller, String from, String to, String pin) throws Exception {
        return mockMvc.perform(post("/api/transfer").header("Authorization", bearer(caller))
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fromCardNumber\":\"" + from + "\",\"toCardNumber\":\"" + to
                        + "\",\"amount\":10.00,\"pin\":\"" + pin + "\"}"));
    }
}
