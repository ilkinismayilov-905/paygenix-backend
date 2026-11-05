package com.example.paygenix.security;

import com.example.paygenix.entity.User;
import com.example.paygenix.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.hamcrest.Matchers.instanceOf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// T-115 / ADR 0001: money travels as decimal strings, never JSON numbers (no float rounding in the browser).
class AmountFormatTest extends IntegrationTest {

    @Test
    void amountsAreDecimalStrings() throws Exception {
        User user = createUser();
        String from = issueCard(user, "AZN");
        String to = issueCard(createUser(), "AZN");
        setBalance(from, "100.5");

        // a string amount is accepted, and the response echoes a string
        mockMvc.perform(post("/api/transfer").header("Authorization", bearer(user))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromCardNumber\":\"" + from + "\",\"toCardNumber\":\"" + to + "\",\"amount\":\"10.00\",\"pin\":\"" + PIN + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.amount").value("10.00"))
                .andExpect(jsonPath("$.data.amount", instanceOf(String.class)));

        mockMvc.perform(get("/api/cards/me").header("Authorization", bearer(user)))
                .andExpect(jsonPath("$.data[0].amount").value("90.30")) // 100.50 - 10.00 - 2% commission
                .andExpect(jsonPath("$.data[0].displayAmount").exists());
    }

    @Test
    void paymentAmountIsAString() throws Exception {
        User user = createUser();
        String card = issueCard(user, "USD");
        setBalance(card, "50.00");
        String flow = com.jayway.jsonpath.JsonPath.read(mockMvc.perform(post("/api/payment/initiate").header("Authorization", bearer(user))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phoneNumber\":\"+994501234567\",\"fromCardNumber\":\"" + card + "\",\"amount\":\"5.25\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.data");

        mockMvc.perform(post("/api/payment/confirm").header("Authorization", bearer(user))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"flowId\":\"" + flow + "\",\"pin\":\"" + PIN + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.amount").value("5.25"));
    }
}
