package com.example.paygenix.flows;

import com.example.paygenix.entity.User;
import com.example.paygenix.enums.Currency;
import com.example.paygenix.support.IntegrationTest;
import com.example.paygenix.topup.CheckoutGateway;
import com.jayway.jsonpath.JsonPath;
import com.stripe.net.Webhook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// T-064: a top-up opens a Stripe Checkout; only a signed webhook credits the card, exactly once.
class TopUpTest extends IntegrationTest {

    static final String SECRET = "whsec_test_only_secret";
    static final AtomicInteger SESSIONS = new AtomicInteger();

    @MockitoBean CheckoutGateway gateway;

    @BeforeEach
    void checkout() {
        reset(gateway);
        when(gateway.open(any(), any(), any(), anyString(), anyString(), anyString())).thenAnswer(inv -> {
            String id = "cs_test_" + SESSIONS.incrementAndGet() + "_" + UUID.randomUUID();
            return new CheckoutGateway.Checkout(id, "https://checkout.stripe.test/" + id);
        });
    }

    private ResultActions start(User user, Long cardId, String amount, String key) throws Exception {
        return mockMvc.perform(post("/api/v1/topups").header("Authorization", bearer(user)).header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content("{\"cardId\":" + cardId + ",\"amount\":\"" + amount + "\"}"));
    }

    private String sessionOf(ResultActions r) throws Exception {
        String url = JsonPath.read(r.andReturn().getResponse().getContentAsString(), "$.data.checkoutUrl");
        return url.substring(url.lastIndexOf('/') + 1);
    }

    private ResultActions webhook(String payload, String secret) throws Exception {
        long ts = System.currentTimeMillis() / 1000;
        String sig = "t=" + ts + ",v1=" + Webhook.Util.computeHmacSha256(secret, ts + "." + payload);
        return mockMvc.perform(post("/api/v1/stripe/webhook").header("Stripe-Signature", sig)
                .contentType(MediaType.APPLICATION_JSON).content(payload));
    }

    private static String event(String type, String session, String paymentStatus, long amount, String currency) {
        return """
                {"id":"evt_1","type":"%s","data":{"object":{"id":"%s","object":"checkout.session","payment_status":"%s",
                "amount_total":%d,"currency":"%s"}}}""".formatted(type, session, paymentStatus, amount, currency);
    }

    @Test
    void aPaidCheckoutCreditsTheCardOnceAndTheBooksBalance() throws Exception {
        User user = createUser();
        String pan = issueCard(user, "AZN");
        Long cardId = cardOf(pan).getId();
        String session = sessionOf(start(user, cardId, "25.50", "k1").andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.amount").value("25.50"))
                .andExpect(jsonPath("$.data.currency").value("AZN")));
        verify(gateway).open(eq(user.getId()), eq(new BigDecimal("25.50")), eq(Currency.AZN), eq("topup-" + user.getId() + "-" + java.util.HexFormat.of().formatHex(
                        java.security.MessageDigest.getInstance("SHA-256").digest("k1".getBytes()))),
                eq("http://localhost:3000/top-up/return?session={CHECKOUT_SESSION_ID}"), eq("http://localhost:3000/top-up/return?cancelled=1"));
        assertThat(balanceOf(pan)).isEqualByComparingTo("0");

        String paid = event("checkout.session.completed", session, "paid", 2550, "azn");
        webhook(paid, SECRET).andExpect(status().isOk());
        webhook(paid, SECRET).andExpect(status().isOk()); // Stripe redelivers

        assertThat(balanceOf(pan)).isEqualByComparingTo("25.50");
        mockMvc.perform(get("/api/v1/topups/session/{s}", session).header("Authorization", bearer(user)))
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.checkoutUrl").doesNotExist());
        mockMvc.perform(get("/api/v1/cards/me/activity").header("Authorization", bearer(user)))
                .andExpect(jsonPath("$.data.items[0].kind").value("TOP_UP"))
                .andExpect(jsonPath("$.data.items[0].amount").value("25.50"));
        assertBooksBalanced();

        // its checkout is used up: the same key cannot reopen it
        start(user, cardId, "25.50", "k1").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TOP_UP_FINISHED"));
    }

    @Test
    void aMalformedSignatureHeaderIsABadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/stripe/webhook").header("Stripe-Signature", "t=abc,v1=00")
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
    }

    @Test
    void forgedUnpaidOrMismatchedEventsMoveNoMoney() throws Exception {
        User user = createUser();
        String pan = issueCard(user, "USD");
        String session = sessionOf(start(user, cardOf(pan).getId(), "10.00", "k"));

        webhook(event("checkout.session.completed", session, "paid", 1000, "usd"), "whsec_wrong").andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/stripe/webhook").contentType(MediaType.APPLICATION_JSON)
                .content(event("checkout.session.completed", session, "paid", 1000, "usd"))).andExpect(status().isBadRequest());
        webhook(event("checkout.session.completed", session, "unpaid", 1000, "usd"), SECRET).andExpect(status().isOk());
        assertThat(balanceOf(pan)).isEqualByComparingTo("0");

        webhook(event("checkout.session.completed", session, "paid", 999999, "usd"), SECRET).andExpect(status().isOk());
        assertThat(balanceOf(pan)).isEqualByComparingTo("0");
        mockMvc.perform(get("/api/v1/topups/session/{s}", session).header("Authorization", bearer(user)))
                .andExpect(jsonPath("$.data.status").value("REFUND_NEEDED"));
    }

    @Test
    void anExpiredCheckoutIsMarked() throws Exception {
        User user = createUser();
        String pan = issueCard(user, "EUR");
        String session = sessionOf(start(user, cardOf(pan).getId(), "5.00", "k"));
        webhook(event("checkout.session.expired", session, "unpaid", 500, "eur"), SECRET).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/topups/session/{s}", session).header("Authorization", bearer(user)))
                .andExpect(jsonPath("$.data.status").value("EXPIRED"));
    }

    @Test
    void aRetryWithTheSameKeyReturnsTheSameCheckout() throws Exception {
        User user = createUser();
        Long cardId = cardOf(issueCard(user, "AZN")).getId();
        String first = sessionOf(start(user, cardId, "10.00", "same"));
        String again = sessionOf(start(user, cardId, "10", "same").andExpect(status().isCreated()));
        assertThat(again).isEqualTo(first);
        verify(gateway, times(1)).open(any(), any(), any(), anyString(), anyString(), anyString());
        start(user, cardId, "11.00", "same").andExpect(status().isUnprocessableContent());
    }

    @Test
    void onlyOwnOpenCardsAndSaneAmounts() throws Exception {
        User user = createUser();
        Long foreign = cardOf(issueCard(createUser(), "AZN")).getId();
        Long own = cardOf(issueCard(user, "AZN")).getId();
        start(user, foreign, "10.00", "a").andExpect(status().isNotFound());
        start(user, own, "0.99", "b").andExpect(status().isBadRequest());
        start(user, own, "10000.01", "c").andExpect(status().isBadRequest());
        start(user, own, "1.001", "d").andExpect(status().isBadRequest());
        verify(gateway, never()).open(any(), any(), any(), anyString(), anyString(), anyString());
        String mine = sessionOf(start(user, own, "1.00", "e").andExpect(status().isCreated()));
        mockMvc.perform(get("/api/v1/topups/session/{s}", mine).header("Authorization", bearer(createUser())))
                .andExpect(status().isNotFound());
    }

    @Test
    void aCardClosedBeforeThePaymentArrivesIsFlaggedForRefund() throws Exception {
        User user = createUser();
        String pan = issueCard(user, "AZN");
        Long cardId = cardOf(pan).getId();
        String session = sessionOf(start(user, cardId, "3.00", "k"));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/cards/{id}", cardId)
                .header("Authorization", bearer(user))).andExpect(status().isOk());

        webhook(event("checkout.session.completed", session, "paid", 300, "azn"), SECRET).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/topups/session/{s}", session).header("Authorization", bearer(user)))
                .andExpect(jsonPath("$.data.status").value("REFUND_NEEDED"));
        assertBooksBalanced();
    }
}
