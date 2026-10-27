package com.example.paygenix.security;

import com.example.paygenix.entity.Cards;
import com.example.paygenix.enums.Currency;
import com.example.paygenix.repository.CardRepository;
import com.example.paygenix.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

// SEC-1: add-amount let any caller credit any card with money from nowhere.
class AddAmountRemovedTest extends IntegrationTest {

    @Autowired CardRepository cardRepository;

    @Test
    void addAmountCannotCreditACard() throws Exception {
        String number = "4000001234567899";
        Cards card = cardRepository.save(Cards.builder()
                .owner(createUser())
                .last4("7899")
                .panHmac(UUID.randomUUID().toString())
                .expirationDate("2030-12")
                .cardHolderName("TEST HOLDER")
                .currency(Currency.AZN)
                .amount(new BigDecimal("10.00"))
                .build());

        try {
            int status = mockMvc.perform(post("/api/cards/" + number + "/add-amount")
                    .param("amount", "1000000")
                    .header("Authorization", bearer(createUser())))
                    .andReturn().getResponse().getStatus();

            // The endpoint must not exist. Before removal it returned 500 only because a Redis cache
            // serialization bug rolled the transaction back; fixing that bug would have re-enabled it.
            // Unknown routes currently map to 401 in GlobalHandlerException (separate cleanup).
            assertThat(status).isIn(401, 404);
            assertThat(cardRepository.findById(card.getId()).orElseThrow().getAmount()).isEqualByComparingTo("10.00");
        } finally {
            // shared container DB: leave no card behind for other tests' list endpoints
            cardRepository.delete(card);
        }
    }
}
