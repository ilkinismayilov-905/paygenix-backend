package com.example.paygenix.flows;

import com.example.paygenix.entity.User;
import com.example.paygenix.service.Housekeeping;
import com.example.paygenix.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HousekeepingTest extends IntegrationTest {

    @Autowired Housekeeping housekeeping;

    @Test
    void idempotencyKeysOlderThanADayArePurged() {
        User user = createUser();
        jdbc.update("INSERT INTO idempotency_keys (user_id, idem_key, request_hash, response_body, created_at) VALUES "
                + "(?, 'old', 'h', '{}', now() - interval '25 hours'), (?, 'new', 'h', '{}', now())", user.getId(), user.getId());

        assertThat(housekeeping.purgeIdempotencyKeys()).isPositive();

        assertThat(jdbc.queryForList("SELECT idem_key FROM idempotency_keys WHERE user_id = ?", String.class, user.getId()))
                .containsExactly("new");
    }

    // T-129: the screens show the configured rates
    @Test
    void feeRatesAreReadable() throws Exception {
        mockMvc.perform(get("/api/v1/fees").header("Authorization", bearer(createUser())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.commissionPercent").value("2"))
                .andExpect(jsonPath("$.data.cashbackPercent").value("0.5"));
    }
}
