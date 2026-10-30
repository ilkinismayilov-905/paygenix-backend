package com.example.paygenix.security;

import com.example.paygenix.dto.request.TransferRequest;
import com.example.paygenix.dto.response.TransferResponse;
import com.example.paygenix.entity.User;
import com.example.paygenix.service.IdempotencyService;
import com.example.paygenix.service.TransferService;
import com.example.paygenix.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// T-03D: a money-moving request retried with the same Idempotency-Key moves money once.
class IdempotencyTest extends IntegrationTest {

    @Autowired IdempotencyService idempotencyService;
    @Autowired TransferService transferService;

    @Test
    void retryWithSameKeyReplaysWithoutMovingMoneyAgain() throws Exception {
        User sender = createUser();
        String from = issueCard(sender, "AZN");
        String to = issueCard(createUser(), "AZN");
        setBalance(from, "100.00");
        String key = UUID.randomUUID().toString();

        transfer(sender, key, from, to, "10.00").andExpect(status().isOk());
        transfer(sender, key, from, to, "10.0") // same amount, different scale
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.amount").value(10.00));

        assertThat(balanceOf(from)).isEqualByComparingTo("89.80");
        assertThat(balanceOf(to)).isEqualByComparingTo("10.00");
    }

    @Test
    void sameKeyWithDifferentRequestIsRejected() throws Exception {
        User sender = createUser();
        String from = issueCard(sender, "AZN");
        String to = issueCard(createUser(), "AZN");
        setBalance(from, "100.00");
        String key = UUID.randomUUID().toString();

        transfer(sender, key, from, to, "10.00").andExpect(status().isOk());
        transfer(sender, key, from, to, "20.00")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));

        assertThat(balanceOf(from)).isEqualByComparingTo("89.80");
    }

    @Test
    void keysAreScopedPerUser() throws Exception {
        User a = createUser();
        User b = createUser();
        String cardA = issueCard(a, "AZN");
        String cardB = issueCard(b, "AZN");
        setBalance(cardA, "100.00");
        setBalance(cardB, "100.00");
        String key = UUID.randomUUID().toString();

        transfer(a, key, cardA, cardB, "10.00").andExpect(status().isOk());
        transfer(b, key, cardB, cardA, "10.00").andExpect(status().isOk());

        assertThat(balanceOf(cardA)).isEqualByComparingTo("99.80");
        assertThat(balanceOf(cardB)).isEqualByComparingTo("99.80");
    }

    @Test
    void failedAttemptDoesNotConsumeTheKey() throws Exception {
        User sender = createUser();
        String from = issueCard(sender, "AZN");
        String to = issueCard(createUser(), "AZN");
        String key = UUID.randomUUID().toString();

        transfer(sender, key, from, to, "10.00").andExpect(status().isConflict()); // no balance yet
        setBalance(from, "100.00");
        transfer(sender, key, from, to, "10.00").andExpect(status().isOk());

        assertThat(balanceOf(from)).isEqualByComparingTo("89.80");
    }

    @Test
    void missingKeyIsRejected() throws Exception {
        User sender = createUser();
        String from = issueCard(sender, "AZN");
        setBalance(from, "100.00");

        mockMvc.perform(post("/api/transfer").header("Authorization", bearer(sender))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(from, issueCard(createUser(), "AZN"), "10.00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_HEADER"));

        assertThat(balanceOf(from)).isEqualByComparingTo("100.00");
    }

    @Test
    void parallelRetriesWithSameKeyMoveMoneyOnce() throws Exception {
        User sender = createUser();
        String from = issueCard(sender, "AZN");
        String to = issueCard(createUser(), "AZN");
        setBalance(from, "100.00");
        String key = UUID.randomUUID().toString();
        TransferRequest request = new TransferRequest(from, to, new BigDecimal("10.00"), PIN);

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<TransferResponse>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return idempotencyService.execute(sender, key, "transfer|" + from + "|" + to + "|10",
                            TransferResponse.class, () -> transferService.transfer(sender, request));
                }));
            }
            start.countDown();
            for (Future<TransferResponse> f : futures) {
                assertThat(f.get(60, TimeUnit.SECONDS).amount()).isEqualByComparingTo("10.00");
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(balanceOf(from)).isEqualByComparingTo("89.80");
    }

    private ResultActions transfer(User caller, String key, String from, String to, String amount) throws Exception {
        return mockMvc.perform(post("/api/transfer").header("Authorization", bearer(caller))
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(from, to, amount)));
    }

    private static String body(String from, String to, String amount) {
        return "{\"fromCardNumber\":\"" + from + "\",\"toCardNumber\":\"" + to
                + "\",\"amount\":" + amount + ",\"pin\":\"" + PIN + "\"}";
    }
}
