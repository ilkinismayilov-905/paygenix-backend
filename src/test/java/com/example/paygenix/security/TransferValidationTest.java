package com.example.paygenix.security;

import com.example.paygenix.entity.User;
import com.example.paygenix.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

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

        transfer(sender, from, to, "10.00", PIN).andExpect(status().isOk());

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

        int status = transfer(sender, from, to, "10.00", "000000").andReturn().getResponse().getStatus();

        assertThat(status).isGreaterThanOrEqualTo(400).isLessThan(500);
        assertThat(balanceOf(from)).isEqualByComparingTo("100.00");
    }

    @Test
    void malformedCardNumberIsBadRequest() throws Exception {
        User sender = createUser();
        transfer(sender, "1234", issueCard(createUser(), "AZN"), "10.00", PIN).andExpect(status().isBadRequest());
    }

    private ResultActions transfer(User caller, String from, String to, String amount, String pin) throws Exception {
        return mockMvc.perform(post("/api/transfer").header("Authorization", bearer(caller))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fromCardNumber\":\"" + from + "\",\"toCardNumber\":\"" + to
                        + "\",\"amount\":" + amount + ",\"pin\":\"" + pin + "\"}"));
    }
}
