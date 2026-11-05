package com.example.paygenix.security;

import com.example.paygenix.entity.Cards;
import com.example.paygenix.entity.User;
import com.example.paygenix.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// T-073: a user sees the money movements of their own cards only, newest first, one page at a time.
class OwnActivityTest extends IntegrationTest {

    @Test
    void senderAndReceiverEachSeeOnlyTheirOwnSide() throws Exception {
        User sender = createUser();
        String from = issueCard(sender, "AZN");
        User receiver = createUser();
        String to = issueCard(receiver, "AZN");
        setBalance(from, "100.00");
        transfer(sender, from, to, "10.00").andExpect(status().isOk());

        activity(sender, "").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(2)))
                .andExpect(jsonPath("$.data.items[0].kind").value("TRANSFER"))
                .andExpect(jsonPath("$.data.items[0].amount").value("-10.20"))
                .andExpect(jsonPath("$.data.items[0].currency").value("AZN"))
                .andExpect(jsonPath("$.data.items[0].createdAt").value(startsWith("20")))
                .andExpect(jsonPath("$.data.items[0].last4").value(from.substring(12)))
                .andExpect(jsonPath("$.data.items[1].kind").value("TOP_UP"))
                .andExpect(jsonPath("$.data.items[1].amount").value("100.00"))
                .andExpect(jsonPath("$.data.hasNext").value(false));

        activity(receiver, "").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].amount").value("10.00"))
                .andExpect(jsonPath("$.data.items[0].cardId").value(cardOf(to).getId()));
    }

    @Test
    void cardIdNarrowsToOneCardAndAForeignCardShowsNothing() throws Exception {
        User owner = createUser();
        String first = issueCard(owner, "AZN");
        String second = issueCard(owner, "USD");
        setBalance(first, "5.00");
        setBalance(second, "7.00");
        Cards foreign = cardOf(issueCard(createUser(), "AZN"));
        setBalance(foreign, "9.00");

        activity(owner, "cardId=" + cardOf(second).getId())
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].amount").value("7.00"))
                .andExpect(jsonPath("$.data.items[0].currency").value("USD"));
        activity(owner, "cardId=" + foreign.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(0)));
    }

    @Test
    void pagesAreNewestFirstAndSayWhetherMoreExist() throws Exception {
        User owner = createUser();
        String card = issueCard(owner, "AZN");
        setBalance(card, "1.00");
        setBalance(card, "3.00");
        setBalance(card, "6.00");

        activity(owner, "size=2")
                .andExpect(jsonPath("$.data.items[*].amount").value(contains("3.00", "2.00")))
                .andExpect(jsonPath("$.data.hasNext").value(true));
        activity(owner, "size=2&page=1")
                .andExpect(jsonPath("$.data.items[*].amount").value(contains("1.00")))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    void closedCardKeepsItsHistory() throws Exception {
        User owner = createUser();
        Cards card = cardOf(issueCard(owner, "AZN"));
        setBalance(card, "4.00");
        setBalance(card, "0.00");
        mockMvc.perform(delete("/api/cards/" + card.getId()).header("Authorization", bearer(owner)))
                .andExpect(status().isOk());

        activity(owner, "").andExpect(jsonPath("$.data.items", hasSize(2)));
    }

    @Test
    void badPagingIsRejectedAndAnonymousIsUnauthorized() throws Exception {
        User owner = createUser();
        activity(owner, "size=101").andExpect(status().isBadRequest());
        activity(owner, "size=0").andExpect(status().isBadRequest());
        activity(owner, "page=-1").andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/cards/me/activity")).andExpect(status().isUnauthorized());
    }

    private ResultActions activity(User user, String query) throws Exception {
        return mockMvc.perform(get("/api/cards/me/activity?" + query).header("Authorization", bearer(user)));
    }

    private ResultActions transfer(User caller, String from, String to, String amount) throws Exception {
        return mockMvc.perform(post("/api/transfer").header("Authorization", bearer(caller))
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fromCardId\":" + cardOf(from).getId() + ",\"toCardNumber\":\"" + to
                        + "\",\"amount\":\"" + amount + "\",\"pin\":\"" + PIN + "\"}"));
    }
}
