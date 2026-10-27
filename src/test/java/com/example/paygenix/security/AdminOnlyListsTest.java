package com.example.paygenix.security;

import com.example.paygenix.enums.Role;
import com.example.paygenix.support.IntegrationTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

// SEC-11: endpoints that list every user's data are admin-only.
class AdminOnlyListsTest extends IntegrationTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/user",
            "/api/profile",
            "/api/transaction",
            "/api/transaction/1",
            "/api/transaction/between?start=01-01-2020&end=01-01-2030",
            "/api/transfer",
            "/api/transfer/1",
            "/api/payment/history",
            "/api/reports",
            "/api/cards"
    })
    void regularUserIsForbidden(String path) throws Exception {
        assertThat(statusOf(path, bearer(createUser()))).isEqualTo(403);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/user", "/api/profile", "/api/transaction", "/api/transfer",
            "/api/payment/history", "/api/reports", "/api/cards"})
    void adminCanList(String path) throws Exception {
        assertThat(statusOf(path, bearer(createUser(Role.ADMIN_ROLE)))).isEqualTo(200);
    }

    private int statusOf(String path, String authorization) throws Exception {
        return mockMvc.perform(get(path).header("Authorization", authorization)).andReturn().getResponse().getStatus();
    }
}
