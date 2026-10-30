package com.example.paygenix.security;

import com.example.paygenix.dto.request.TransferRequest;
import com.example.paygenix.entity.Cards;
import com.example.paygenix.entity.User;
import com.example.paygenix.exceptions.InsufficientBalanceException;
import com.example.paygenix.service.TransferService;
import com.example.paygenix.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// T-060: money is never created or lost. After any sequence of operations every posting sums to zero,
// all entries per currency sum to zero, and each card's cached balance equals its ledger balance.
class LedgerInvariantTest extends IntegrationTest {

    @Autowired TransferService transferService;
    @Autowired JdbcTemplate jdbc;

    @Test
    void randomTransfersAndPaymentsKeepTheBooksBalanced() throws Exception {
        Random random = new Random(42);
        List<User> users = new ArrayList<>();
        List<String> cards = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            User user = createUser();
            String card = issueCard(user, "AZN");
            setBalance(card, "500.00");
            users.add(user);
            cards.add(card);
        }

        for (int i = 0; i < 40; i++) {
            int from = random.nextInt(cards.size());
            int to = (from + 1 + random.nextInt(cards.size() - 1)) % cards.size();
            String amount = BigDecimal.valueOf(1 + random.nextInt(20000), 2).toPlainString(); // 0.01 .. 200.00
            if (random.nextBoolean()) {
                try {
                    transferService.transfer(users.get(from),
                            new TransferRequest(cards.get(from), cards.get(to), new BigDecimal(amount), PIN));
                } catch (InsufficientBalanceException rejected) {
                    // rejected operations must leave no trace in the books
                }
            } else {
                pay(users.get(from), cards.get(from), amount);
            }
        }

        assertBooksBalanced();
        for (String card : cards) {
            Cards stored = cardOf(card);
            assertThat(stored.getAmount()).isEqualByComparingTo(ledgerService.balanceOf(stored));
        }
    }

    @Test
    void transferBooksCommissionAsRevenue() throws Exception {
        User sender = createUser();
        String from = issueCard(sender, "EUR");
        String to = issueCard(createUser(), "EUR");
        setBalance(from, "100.00");
        BigDecimal revenueBefore = systemBalance("FEE_REVENUE", "EUR");

        transferService.transfer(sender, new TransferRequest(from, to, new BigDecimal("10.00"), PIN));

        assertThat(systemBalance("FEE_REVENUE", "EUR").subtract(revenueBefore)).isEqualByComparingTo("0.20");
        assertBooksBalanced();
    }

    @Test
    void smallPaymentWithZeroCashbackIsBookedWithoutCashback() throws Exception {
        User payer = createUser();
        String card = issueCard(payer, "AZN");
        setBalance(card, "10.00");

        pay(payer, card, "1.99"); // 0.5% rounds down to 0.00

        assertThat(balanceOf(card)).isEqualByComparingTo("8.01");
        assertBooksBalanced();
    }

    @Test
    void entriesCannotBeChangedOrDeleted() {
        assertThatThrownBy(() -> jdbc.update("UPDATE ledger_entries SET amount = amount * 2"))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM ledger_entries"))
                .hasMessageContaining("append-only");
    }

    private void pay(User payer, String card, String amount) throws Exception {
        var initiated = mockMvc.perform(post("/api/payment/initiate").header("Authorization", bearer(payer))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phoneNumber\":\"+994501234567\",\"fromCardNumber\":\"" + card
                                + "\",\"amount\":" + amount + "}"))
                .andReturn().getResponse();
        if (initiated.getStatus() == 409) {
            return; // insufficient balance at initiate
        }
        assertThat(initiated.getStatus()).isEqualTo(200);
        String flowId = JsonPath.read(initiated.getContentAsString(), "$.data");
        mockMvc.perform(post("/api/payment/confirm").header("Authorization", bearer(payer))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"flowId\":\"" + flowId + "\",\"pin\":\"" + PIN + "\"}"))
                .andExpect(status().isOk());
    }

    // Checked across the whole database, so every other test's postings are covered too.
    private void assertBooksBalanced() {
        assertThat(jdbc.queryForList(
                "SELECT tx_id FROM ledger_entries GROUP BY tx_id HAVING SUM(amount) <> 0 OR COUNT(DISTINCT currency) > 1"))
                .isEmpty();
        for (Map<String, Object> row : jdbc.queryForList("SELECT currency, SUM(amount) AS total FROM ledger_entries GROUP BY currency")) {
            assertThat((BigDecimal) row.get("total")).as("sum of %s entries", row.get("currency")).isZero();
        }
        assertThat(jdbc.queryForList("""
                SELECT c.id FROM cards c LEFT JOIN ledger_accounts a ON a.card_id = c.id
                LEFT JOIN ledger_entries e ON e.account_id = a.id
                GROUP BY c.id, c.amount HAVING c.amount <> COALESCE(SUM(e.amount), 0)""")).isEmpty();
    }

    private BigDecimal systemBalance(String code, String currency) {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(e.amount), 0) FROM ledger_entries e JOIN ledger_accounts a ON a.id = e.account_id
                WHERE a.code = ? AND a.currency = ?""", BigDecimal.class, code, currency);
    }
}
