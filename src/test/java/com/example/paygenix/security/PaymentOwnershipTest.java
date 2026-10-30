package com.example.paygenix.security;

import com.example.paygenix.entity.User;
import com.example.paygenix.exceptions.PaymentAlreadyProcessedException;
import com.example.paygenix.service.PaymentService;
import com.example.paygenix.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

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

// SEC-7 / FLOW-1: a payment is paid only from the caller's own card, with a positive amount,
// and only the user who started a flow can confirm it, exactly once.
class PaymentOwnershipTest extends IntegrationTest {

    private static final String PHONE = "+994501234567";

    @Autowired PaymentService paymentService;

    @Test
    void ownerCanInitiateAndConfirm() throws Exception {
        User payer = createUser();
        String card = issueCard(payer, "AZN");
        setBalance(card, "100.00");

        confirm(payer, initiate(payer, card, "10.00"), PIN).andExpect(status().isOk());

        assertThat(balanceOf(card)).isEqualByComparingTo("90.05"); // 10.00 minus 0.5% cashback (unfunded, T-061)
    }

    @Test
    void paymentCarriesItsCardsCurrency() throws Exception {
        User payer = createUser();
        String card = issueCard(payer, "USD");
        setBalance(card, "100.00");

        confirm(payer, initiate(payer, card, "10.00"), PIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currency").value("USD"));
    }

    @Test
    void largePaymentCashbackFitsTheColumn() throws Exception {
        User payer = createUser();
        String card = issueCard(payer, "AZN");
        setBalance(card, "300000.00");

        confirm(payer, initiate(payer, card, "200000.00"), PIN).andExpect(status().isOk()); // cashback 1000.00

        assertThat(balanceOf(card)).isEqualByComparingTo("101000.00");
    }

    @Test
    void cannotInitiateFromSomeoneElsesCard() throws Exception {
        String victimCard = issueCard(createUser(), "AZN");
        setBalance(victimCard, "100.00");

        initiateRequest(createUser(), victimCard, "10.00").andExpect(status().isNotFound());
    }

    @Test
    void cannotConfirmSomeoneElsesFlow() throws Exception {
        User victim = createUser();
        String card = issueCard(victim, "AZN");
        setBalance(card, "100.00");
        String flowId = initiate(victim, card, "10.00");

        confirm(createUser(), flowId, PIN).andExpect(status().isNotFound());
        assertThat(balanceOf(card)).isEqualByComparingTo("100.00");

        // the victim's flow is untouched and still confirmable
        confirm(victim, flowId, PIN).andExpect(status().isOk());
    }

    @Test
    void unknownFlowIsNotFound() throws Exception {
        confirm(createUser(), "no-such-flow", PIN).andExpect(status().isNotFound());
    }

    @Test
    void nonPositiveAmountIsRejected() throws Exception {
        User payer = createUser();
        String card = issueCard(payer, "AZN");
        setBalance(card, "100.00");

        initiateRequest(payer, card, "-10.00").andExpect(status().isBadRequest());
        initiateRequest(payer, card, "0").andExpect(status().isBadRequest());
        initiateRequest(payer, card, "null").andExpect(status().isBadRequest());
        initiateRequest(payer, card, "10.001").andExpect(status().isBadRequest());
    }

    @Test
    void wrongPinLeavesFlowPending() throws Exception {
        User payer = createUser();
        String card = issueCard(payer, "AZN");
        setBalance(card, "100.00");
        String flowId = initiate(payer, card, "10.00");

        // 404 today (PinValidationService); expected to become 401/403 with PIN attempt limits (T-040)
        confirm(payer, flowId, "000000").andExpect(status().isNotFound());
        assertThat(balanceOf(card)).isEqualByComparingTo("100.00");

        confirm(payer, flowId, PIN).andExpect(status().isOk());
    }

    @Test
    void balanceIsRecheckedAtConfirm() throws Exception {
        User payer = createUser();
        String card = issueCard(payer, "AZN");
        setBalance(card, "100.00");
        String flowId = initiate(payer, card, "10.00");
        setBalance(card, "5.00");

        confirm(payer, flowId, PIN)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_BALANCE"));
        assertThat(balanceOf(card)).isEqualByComparingTo("5.00");
    }

    @Test
    void flowCanBeConfirmedOnlyOnce() throws Exception {
        User payer = createUser();
        String card = issueCard(payer, "AZN");
        setBalance(card, "100.00");
        String flowId = initiate(payer, card, "10.00");

        confirm(payer, flowId, PIN).andExpect(status().isOk());
        confirm(payer, flowId, PIN)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_ALREADY_PROCESSED"));

        assertThat(balanceOf(card)).isEqualByComparingTo("90.05");
    }

    @Test
    void parallelConfirmsOfOneFlowChargeOnce() throws Exception {
        User payer = createUser();
        String card = issueCard(payer, "AZN");
        setBalance(card, "100.00");
        String flowId = initiate(payer, card, "10.00");

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    try {
                        paymentService.confirmPayment(flowId, PIN, payer);
                        return true;
                    } catch (PaymentAlreadyProcessedException rejected) {
                        return false;
                    }
                }));
            }
            start.countDown();
            long succeeded = 0;
            for (Future<Boolean> f : futures) {
                if (f.get(60, TimeUnit.SECONDS)) succeeded++;
            }
            assertThat(succeeded).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
        assertThat(balanceOf(card)).isEqualByComparingTo("90.05");
    }

    private String initiate(User payer, String card, String amount) throws Exception {
        String body = initiateRequest(payer, card, amount).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data");
    }

    private ResultActions initiateRequest(User payer, String card, String amount) throws Exception {
        return mockMvc.perform(post("/api/payment/initiate").header("Authorization", bearer(payer))
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"phoneNumber\":\"" + PHONE + "\",\"fromCardNumber\":\"" + card
                        + "\",\"amount\":" + amount + "}"));
    }

    private ResultActions confirm(User caller, String flowId, String pin) throws Exception {
        return mockMvc.perform(post("/api/payment/confirm").header("Authorization", bearer(caller))
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"flowId\":\"" + flowId + "\",\"pin\":\"" + pin + "\"}"));
    }
}
