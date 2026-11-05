package com.example.paygenix.security;

import com.example.paygenix.entity.User;
import com.example.paygenix.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// SEC-2 / FLOW-3: a transfer moves money only from the caller's own card, with PIN, a positive amount,
// enough balance for amount + commission, two different cards and one currency.
class TransferValidationTest extends IntegrationTest {

    @Test
    void validTransferMovesAmountAndChargesCommission() throws Exception {
        User sender = createUser();
        String from = issueCard(sender, "AZN");
        String to = issueCard(createUser(), "AZN");
        setBalance(from, "100.00");

        transfer(sender, from, to, "10.00", PIN).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currency").value("AZN")) // T-126
                .andExpect(jsonPath("$.data.fromCardNumber").value("**** **** **** " + from.substring(12)));

        assertThat(balanceOf(from)).isEqualByComparingTo("89.80"); // 10.00 + 2% commission
        assertThat(balanceOf(to)).isEqualByComparingTo("10.00");
    }

    @Test
    void cannotSpendFromSomeoneElsesCard() throws Exception {
        String victim = issueCard(createUser(), "AZN");
        setBalance(victim, "100.00");
        User thief = createUser();
        String thiefCard = issueCard(thief, "AZN");

        transfer(thief, victim, thiefCard, "50.00", PIN).andExpect(status().isNotFound());

        assertThat(balanceOf(victim)).isEqualByComparingTo("100.00");
        assertThat(balanceOf(thiefCard)).isEqualByComparingTo("0.00");
    }

    @Test
    void negativeAmountIsRejectedAndCannotPullMoney() throws Exception {
        User sender = createUser();
        String from = issueCard(sender, "AZN");
        String to = issueCard(createUser(), "AZN");
        setBalance(to, "100.00");

        transfer(sender, from, to, "-50.00", PIN).andExpect(status().isBadRequest());

        assertThat(balanceOf(to)).isEqualByComparingTo("100.00");
        assertThat(balanceOf(from)).isEqualByComparingTo("0.00");
    }

    @Test
    void commissionCannotOverdraw() throws Exception {
        User sender = createUser();
        String from = issueCard(sender, "AZN");
        String to = issueCard(createUser(), "AZN");
        setBalance(from, "10.10");

        transfer(sender, from, to, "10.00", PIN) // needs 10.20
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_BALANCE"));

        assertThat(balanceOf(from)).isEqualByComparingTo("10.10");
    }

    @Test
    void wholeBalanceCanBeSpentExactly() throws Exception {
        User sender = createUser();
        String from = issueCard(sender, "AZN");
        String to = issueCard(createUser(), "AZN");
        setBalance(from, "10.20");

        transfer(sender, from, to, "10.00", PIN).andExpect(status().isOk());

        assertThat(balanceOf(from)).isEqualByComparingTo("0.00");
    }

    @Test
    void largeTransferCommissionFitsTheColumn() throws Exception {
        User sender = createUser();
        String from = issueCard(sender, "AZN");
        String to = issueCard(createUser(), "AZN");
        setBalance(from, "60000.00");

        transfer(sender, from, to, "50000.00", PIN).andExpect(status().isOk()); // commission 1000.00

        assertThat(balanceOf(from)).isEqualByComparingTo("9000.00");
    }

    @Test
    void sameCardIsRejected() throws Exception {
        User sender = createUser();
        String card = issueCard(sender, "AZN");
        setBalance(card, "100.00");

        transfer(sender, card, card, "10.00", PIN)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SAME_CARD"));

        assertThat(balanceOf(card)).isEqualByComparingTo("100.00");
    }

    @Test
    void currencyMismatchIsRejected() throws Exception {
        User sender = createUser();
        String from = issueCard(sender, "AZN");
        String to = issueCard(createUser(), "USD");
        setBalance(from, "100.00");

        transfer(sender, from, to, "10.00", PIN)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CURRENCY_MISMATCH"));

        assertThat(balanceOf(from)).isEqualByComparingTo("100.00");
    }

    @Test
    void wrongPinIsRejected() throws Exception {
        User sender = createUser();
        String from = issueCard(sender, "AZN");
        String to = issueCard(createUser(), "AZN");
        setBalance(from, "100.00");

        transfer(sender, from, to, "10.00", "000000")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("INVALID_PIN"));

        assertThat(balanceOf(from)).isEqualByComparingTo("100.00");
    }

    @Test
    void malformedCardNumberIsBadRequest() throws Exception {
        User sender = createUser();
        transfer(sender, issueCard(sender, "AZN"), "1234", "10.00", PIN).andExpect(status().isBadRequest());
    }

    @Test
    void amountWithThreeDecimalsIsRejected() throws Exception {
        User sender = createUser();
        transfer(sender, issueCard(sender, "AZN"), issueCard(createUser(), "AZN"), "10.001", PIN)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void nonNumericAmountIsMalformed() throws Exception {
        User sender = createUser();
        transfer(sender, issueCard(sender, "AZN"), issueCard(createUser(), "AZN"), "\"abc\"", PIN)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void missingSourceCardIsRejected() throws Exception {
        User sender = createUser();
        String to = issueCard(createUser(), "AZN");
        mockMvc.perform(post("/api/transfer").header("Authorization", bearer(sender))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toCardNumber\":\"" + to + "\",\"amount\":\"1.00\",\"pin\":\"" + PIN + "\"}"))
                .andExpect(status().isBadRequest());
    }

    private ResultActions transfer(User caller, String from, String to, String amount, String pin) throws Exception {
        return mockMvc.perform(post("/api/transfer").header("Authorization", bearer(caller))
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fromCardId\":" + cardOf(from).getId() + ",\"toCardNumber\":\"" + to
                        + "\",\"amount\":" + amount + ",\"pin\":\"" + pin + "\"}"));
    }
}
