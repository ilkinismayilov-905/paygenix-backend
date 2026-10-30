package com.example.paygenix.security;

import com.example.paygenix.entity.Cards;
import com.example.paygenix.entity.User;
import com.example.paygenix.support.IntegrationTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// T-03B: a request waiting on a locked card row gives up after lock_timeout instead of hanging.
class LockTimeoutTest extends IntegrationTest {

    private static final long HOLD_MILLIS = 8_000;

    @Autowired EntityManager entityManager;
    @Autowired TransactionTemplate transactionTemplate;

    @Test
    void transferOnLockedCardFailsFastWithConflict() throws Exception {
        User sender = createUser();
        String from = issueCard(sender, "AZN");
        String to = issueCard(createUser(), "AZN");
        setBalance(from, "100.00");
        Long fromId = cardOf(from).getId();

        CountDownLatch locked = new CountDownLatch(1);
        CompletableFuture<Void> holder = CompletableFuture.runAsync(() -> transactionTemplate.executeWithoutResult(tx -> {
            entityManager.find(Cards.class, fromId, LockModeType.PESSIMISTIC_WRITE);
            locked.countDown();
            try {
                Thread.sleep(HOLD_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));
        assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

        long started = System.nanoTime();
        mockMvc.perform(post("/api/transfer").header("Authorization", bearer(sender))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromCardNumber\":\"" + from + "\",\"toCardNumber\":\"" + to
                                + "\",\"amount\":10.00,\"pin\":\"" + PIN + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_BUSY"));
        long waitedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

        assertThat(waitedMillis).isLessThan(HOLD_MILLIS - 2_000);
        holder.get(20, TimeUnit.SECONDS);
        assertThat(balanceOf(from)).isEqualByComparingTo("100.00");
    }
}
