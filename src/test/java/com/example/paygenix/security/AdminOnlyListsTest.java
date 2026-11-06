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
            "/api/v1/user",
            "/api/v1/profile",
            "/api/v1/transaction",
            "/api/v1/transaction/1",
            "/api/v1/transaction/between?start=01-01-2020&end=01-01-2030",
            "/api/v1/transfer",
            "/api/v1/transfer/1",
            "/api/v1/payment/history",
            "/api/v1/reports",
            "/api/v1/cards"
    })
    void regularUserIsForbidden(String path) throws Exception {
        assertThat(statusOf(path, bearer(createUser()))).isEqualTo(403);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/user", "/api/v1/profile", "/api/v1/transaction", "/api/v1/transfer",
            "/api/v1/payment/history", "/api/v1/reports", "/api/v1/cards"})
    void adminCanList(String path) throws Exception {
        assertThat(statusOf(path, bearer(createUser(Role.ADMIN_ROLE)))).isEqualTo(200);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/transaction/1", "/api/v1/transfer/1"})
    void adminPassesAuthorizationOnLookups(String path) throws Exception {
        // no such record in the test DB, so anything but 403 means the admin got through
        assertThat(statusOf(path, bearer(createUser(Role.ADMIN_ROLE)))).isNotEqualTo(403);
    }

    private int statusOf(String path, String authorization) throws Exception {
        return mockMvc.perform(get(path).header("Authorization", authorization)).andReturn().getResponse().getStatus();
    }
}
