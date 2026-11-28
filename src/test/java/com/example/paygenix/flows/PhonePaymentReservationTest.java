package com.example.paygenix.flows;

import com.example.paygenix.entity.User;
import com.example.paygenix.service.PaymentReconciliation;
import com.example.paygenix.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T-144: a phone payment holds the amount, calls the operator with no transaction open, then settles or releases;
 * an unknown answer stays held until a retry or PaymentReconciliation knows more. Mock operator numbers:
 * ...999 never delivers and never answers, ...998 delivers but its answer is lost, ...000 is refused.
 */
class PhonePaymentReservationTest extends IntegrationTest {

    @Autowired PaymentReconciliation reconciliation;

    @Test
    void aDeliveredPaymentMovesTheHoldToThePayout() throws Exception {
        Payer p = payer("100.00");
        String flow = initiate(p, "+994501234567", "10.00");
        confirm(p, flow, UUID.randomUUID().toString()).andExpect(status().isOk()).andExpect(jsonPath("$.data.amount").value("10.00"));
        assertThat(balanceOf(p.card)).isEqualByComparingTo("90.05"); // 0.5% cashback
        assertThat(statusOf(flow)).isEqualTo("COMPLETED");
        assertThat(holdOf(flow)).isEqualByComparingTo("0");
        assertBooksBalanced();
    }

    @Test
    void anUnansweredPaymentStaysHeldThenReconciliationReturnsIt() throws Exception {
        Payer p = payer("100.00");
        String flow = initiate(p, "+994501234999", "10.00");
        String key = UUID.randomUUID().toString();
        confirm(p, flow, key).andExpect(status().isGatewayTimeout()).andExpect(jsonPath("$.code").value("PROVIDER_TIMEOUT"));
        assertThat(balanceOf(p.card)).isEqualByComparingTo("90.00");
        assertThat(statusOf(flow)).isEqualTo("PROCESSING");
        assertThat(holdOf(flow)).isEqualByComparingTo("10.00");

        // a retry with the same key asks again with the same reference: nothing is held twice
        String reference = referenceOf(flow);
        confirm(p, flow, key).andExpect(status().isGatewayTimeout());
        assertThat(balanceOf(p.card)).isEqualByComparingTo("90.00");
        assertThat(referenceOf(flow)).isEqualTo(reference);

        // too recent: an answer may still be on its way
        reconciliation.reconcile();
        assertThat(statusOf(flow)).isEqualTo("PROCESSING");

        jdbc.update("UPDATE payment_onboarding SET reserved_at = now() - interval '3 minutes' WHERE id = ?", flow);
        reconciliation.reconcile();
        assertThat(statusOf(flow)).isEqualTo("PENDING");
        assertThat(balanceOf(p.card)).isEqualByComparingTo("100.00");
        assertThat(holdOf(flow)).isEqualByComparingTo("0");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ledger_entries WHERE reference = ? AND kind = 'PAYMENT_RELEASE'",
                Integer.class, reference)).isEqualTo(2);
        assertBooksBalanced();
    }

    @Test
    void aRetryAfterALostAnswerCompletesThePaymentOnce() throws Exception {
        Payer p = payer("100.00");
        String flow = initiate(p, "+994501234998", "10.00");
        String key = UUID.randomUUID().toString();
        confirm(p, flow, key).andExpect(status().isGatewayTimeout());
        assertThat(balanceOf(p.card)).isEqualByComparingTo("90.00");

        confirm(p, flow, key).andExpect(status().isOk());
        assertThat(balanceOf(p.card)).isEqualByComparingTo("90.05");
        assertThat(statusOf(flow)).isEqualTo("COMPLETED");
        // the same key now replays the result; another key is told it is done
        confirm(p, flow, key).andExpect(status().isOk());
        confirm(p, flow, UUID.randomUUID().toString()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_ALREADY_PROCESSED"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payment_transactions WHERE transaction_id = ?",
                Integer.class, referenceOf(flow))).isEqualTo(1);
        assertThat(balanceOf(p.card)).isEqualByComparingTo("90.05");
        assertBooksBalanced();
    }

    @Test
    void reconciliationSettlesADeliveredPaymentNobodyRetried() throws Exception {
        Payer p = payer("100.00");
        String flow = initiate(p, "+994501234998", "20.00");
        confirm(p, flow, UUID.randomUUID().toString()).andExpect(status().isGatewayTimeout());
        jdbc.update("UPDATE payment_onboarding SET reserved_at = now() - interval '3 minutes' WHERE id = ?", flow);

        reconciliation.reconcile();
        assertThat(statusOf(flow)).isEqualTo("COMPLETED");
        assertThat(balanceOf(p.card)).isEqualByComparingTo("80.10");
        assertThat(holdOf(flow)).isEqualByComparingTo("0");
        reconciliation.reconcile(); // nothing left to do
        assertThat(balanceOf(p.card)).isEqualByComparingTo("80.10");
        assertBooksBalanced();
    }

    @Test
    void aRefusedNumberGetsTheHeldAmountBackAtOnce() throws Exception {
        Payer p = payer("100.00");
        String flow = initiate(p, "+994501234000", "10.00");
        confirm(p, flow, UUID.randomUUID().toString()).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("PHONE_TOPUP_REJECTED"));
        assertThat(balanceOf(p.card)).isEqualByComparingTo("100.00");
        assertThat(statusOf(flow)).isEqualTo("PENDING");
        // shown in the card's activity: the payment and its return
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/cards/me/activity")
                        .header("Authorization", bearer(p.user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.kind == 'PAYMENT_RELEASE')]").exists());
        assertBooksBalanced();
    }

    @Test
    void aCardWithAHeldPaymentCannotBeClosed() throws Exception {
        Payer p = payer("10.00");
        String flow = initiate(p, "+994501234999", "10.00");
        confirm(p, flow, UUID.randomUUID().toString()).andExpect(status().isGatewayTimeout());
        assertThat(balanceOf(p.card)).isEqualByComparingTo("0.00");
        mockMvc.perform(delete("/api/v1/cards/" + cardOf(p.card).getId()).header("Authorization", bearer(p.user)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CARD_HAS_BALANCE"));
    }

    @Test
    void aSecondConfirmWhileTheFirstRunsIsBusyAndADeadOneIsTakenOver() throws Exception {
        Payer p = payer("100.00");
        String flow = initiate(p, "+994501234567", "10.00");
        String key = UUID.randomUUID().toString();
        // what a request that is still running (or died) leaves: the claim, no response yet
        jdbc.update("INSERT INTO idempotency_keys (user_id, idem_key, request_hash, response_body) VALUES (?, ?, ?, '')",
                p.user.getId(), key, panService.hmac("idempotency:payment-confirm|" + flow));
        confirm(p, flow, key).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RESOURCE_BUSY"));
        assertThat(balanceOf(p.card)).isEqualByComparingTo("100.00");

        jdbc.update("UPDATE idempotency_keys SET created_at = now() - interval '6 minutes' WHERE user_id = ? AND idem_key = ?",
                p.user.getId(), key);
        confirm(p, flow, key).andExpect(status().isOk());
        assertThat(balanceOf(p.card)).isEqualByComparingTo("90.05");
    }

    @Test
    void aFailedConfirmGivesItsKeyBack() throws Exception {
        Payer p = payer("100.00");
        String flow = initiate(p, "+994501234567", "10.00");
        String key = UUID.randomUUID().toString();
        confirmWithPin(p, flow, key, "000000").andExpect(status().isForbidden());
        confirm(p, flow, key).andExpect(status().isOk());
        assertThat(balanceOf(p.card)).isEqualByComparingTo("90.05");
    }

    private record Payer(User user, String card) {
    }

    private Payer payer(String balance) throws Exception {
        User user = createUser();
        String card = issueCard(user, "AZN");
        setBalance(card, balance);
        return new Payer(user, card);
    }

    private String initiate(Payer p, String phone, String amount) throws Exception {
        String body = mockMvc.perform(post("/api/v1/payment/initiate").header("Authorization", bearer(p.user))
                        .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phoneNumber\":\"" + phone + "\",\"fromCardId\":" + cardOf(p.card).getId()
                                + ",\"amount\":\"" + amount + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data");
    }

    private ResultActions confirm(Payer p, String flow, String key) throws Exception {
        return confirmWithPin(p, flow, key, PIN);
    }

    private ResultActions confirmWithPin(Payer p, String flow, String key, String pin) throws Exception {
        return mockMvc.perform(post("/api/v1/payment/confirm").header("Authorization", bearer(p.user)).header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content("{\"flowId\":\"" + flow + "\",\"pin\":\"" + pin + "\"}"));
    }

    private String statusOf(String flow) {
        return jdbc.queryForObject("SELECT payment_status FROM payment_onboarding WHERE id = ?", String.class, flow);
    }

    private String referenceOf(String flow) {
        return jdbc.queryForObject("SELECT operator_reference FROM payment_onboarding WHERE id = ?", String.class, flow);
    }

    // what PAYMENT_HOLD still holds for this flow's reservation
    private BigDecimal holdOf(String flow) {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(e.amount), 0) FROM ledger_entries e JOIN ledger_accounts a ON a.id = e.account_id
                WHERE a.code = 'PAYMENT_HOLD' AND e.reference = ?""", BigDecimal.class, referenceOf(flow));
    }
}
