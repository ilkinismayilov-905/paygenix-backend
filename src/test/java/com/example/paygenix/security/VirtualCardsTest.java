package com.example.paygenix.security;

import com.example.paygenix.entity.Cards;
import com.example.paygenix.entity.User;
import com.example.paygenix.repository.CardRepository;
import com.example.paygenix.repository.TransferRepository;
import com.example.paygenix.service.PanService;
import com.example.paygenix.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Gate 1 #4 / SEC-5 / SEC-6: system-issued numbers, never stored or logged, cards scoped to their owner.
@ExtendWith(OutputCaptureExtension.class)
class VirtualCardsTest extends IntegrationTest {

    @Autowired CardRepository cardRepository;
    @Autowired TransferRepository transferRepository;
    @Autowired JdbcTemplate jdbc;

    @Test
    void issuedNumberIsShownOnceAndNeverStoredOrLogged(CapturedOutput output) throws Exception {
        User owner = createUser();
        String pan = issue(owner, "AZN");
        Long id = cardRepository.findByOwnerId(owner.getId()).getFirst().getId();

        assertThat(PanService.isLuhnValid(pan)).isTrue();
        Map<String, Object> row = jdbc.queryForMap("select * from cards where id = ?", id);
        assertThat(row.values()).noneMatch(v -> v != null && v.toString().contains(pan));
        assertThat(row.get("last4")).isEqualTo(pan.substring(12));
        assertThat(output.getAll()).doesNotContain(pan);

        mockMvc.perform(get("/api/cards/" + id).header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.maskedNumber").value("**** **** **** " + pan.substring(12)))
                .andExpect(jsonPath("$.data.currency").value("AZN"));
    }

    @Test
    void myCardsListsOnlyMyCards() throws Exception {
        User a = createUser();
        issue(a, "AZN");
        issue(a, "USD");
        issue(createUser(), "EUR");

        mockMvc.perform(get("/api/cards/me").header("Authorization", bearer(a)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    void anotherUsersCardIsInvisibleAndCannotBeClosed() throws Exception {
        User owner = createUser();
        issue(owner, "AZN");
        Long id = cardRepository.findByOwnerId(owner.getId()).getFirst().getId();
        String intruder = bearer(createUser());

        mockMvc.perform(get("/api/cards/" + id).header("Authorization", intruder)).andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/cards/" + id).header("Authorization", intruder)).andExpect(status().isNotFound());
        assertThat(cardRepository.findById(id)).isPresent();
    }

    @Test
    void cardWithBalanceCannotBeClosedButEmptyOneCan() throws Exception {
        User owner = createUser();
        issue(owner, "AZN");
        Cards card = cardRepository.findByOwnerId(owner.getId()).getFirst();
        setBalance(card, "5.00");

        mockMvc.perform(delete("/api/cards/" + card.getId()).header("Authorization", bearer(owner)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CARD_HAS_BALANCE"));

        setBalance(card, "0.00");
        mockMvc.perform(delete("/api/cards/" + card.getId()).header("Authorization", bearer(owner)))
                .andExpect(status().isOk());
        assertThat(cardRepository.findById(card.getId())).isEmpty();
    }

    @Test
    void transferFindsCardsByFullNumberAndKeepsOnlyLast4InHistory() throws Exception {
        User sender = createUser();
        User recipient = createUser();
        String fromPan = issue(sender, "AZN");
        String toPan = issue(recipient, "AZN");
        Cards from = cardRepository.findByOwnerId(sender.getId()).getFirst();
        Cards to = cardRepository.findByOwnerId(recipient.getId()).getFirst();
        setBalance(from, "100.00");

        mockMvc.perform(post("/api/transfer").header("Authorization", bearer(sender))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromCardNumber\":\"" + fromPan + "\",\"toCardNumber\":\"" + toPan + "\",\"amount\":10.00,\"pin\":\"" + PIN + "\"}"))
                .andExpect(status().isOk());

        assertThat(cardRepository.findById(to.getId()).orElseThrow().getAmount()).isEqualByComparingTo("10.00");
        List<String> stored = jdbc.queryForList("select from_card_number || ',' || to_card_number from transfer", String.class);
        assertThat(stored).contains(fromPan.substring(12) + "," + toPan.substring(12));
        assertThat(stored).noneMatch(s -> s.contains(fromPan) || s.contains(toPan));
    }

    @Test
    void missingCardNumberIsRejectedNotServerError() throws Exception {
        mockMvc.perform(post("/api/transfer").header("Authorization", bearer(createUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":10.00,\"pin\":\"" + PIN + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    private String issue(User owner, String currency) throws Exception {
        String body = mockMvc.perform(post("/api/cards").header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currency\":\"" + currency + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.cardNumber");
    }

    private void setBalance(Cards card, String amount) {
        Cards fresh = cardRepository.findById(card.getId()).orElseThrow();
        fresh.setAmount(new BigDecimal(amount));
        cardRepository.save(fresh);
    }
}
