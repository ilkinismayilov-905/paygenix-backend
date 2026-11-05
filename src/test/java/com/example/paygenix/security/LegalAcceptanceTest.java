package com.example.paygenix.security;

import com.example.paygenix.entity.User;
import com.example.paygenix.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// T-121: money moves need the active Terms and Privacy versions accepted; a new version asks again.
class LegalAcceptanceTest extends IntegrationTest {

    @Test
    void transfersWaitForAcceptance() throws Exception {
        User user = createUserWithoutLegalAcceptance();
        String from = issueCard(user, "AZN");
        String to = issueCard(createUser(), "AZN");
        setBalance(from, "100.00");

        mockMvc.perform(get("/api/auth/me").header("Authorization", bearer(user)))
                .andExpect(jsonPath("$.data.pendingLegalDocuments", hasSize(2)))
                .andExpect(jsonPath("$.data.pendingLegalDocuments[0].type").value("PRIVACY"))
                .andExpect(jsonPath("$.data.pendingLegalDocuments[1].type").value("TERMS"));
        transfer(user, from, to).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("LEGAL_ACCEPTANCE_REQUIRED"));
        mockMvc.perform(post("/api/payment/initiate").header("Authorization", bearer(user))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phoneNumber\":\"+994501234567\",\"fromCardNumber\":\"" + from + "\",\"amount\":10.00}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("LEGAL_ACCEPTANCE_REQUIRED"));

        accept(user, activeLegalDocumentsJson()).andExpect(status().isOk());
        accept(user, activeLegalDocumentsJson()).andExpect(status().isOk()); // repeat is harmless

        mockMvc.perform(get("/api/auth/me").header("Authorization", bearer(user)))
                .andExpect(jsonPath("$.data.pendingLegalDocuments", hasSize(0)));
        transfer(user, from, to).andExpect(status().isOk());
    }

    @Test
    void outdatedOrPartialAcceptanceRecordsNothing() throws Exception {
        User user = createUserWithoutLegalAcceptance();

        accept(user, "[{\"type\":\"TERMS\",\"version\":\"old\"}]").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LEGAL_VERSION_OUTDATED"));
        String termsOnly = "[{\"type\":\"TERMS\",\"version\":\"" + activeVersion("TERMS") + "\"}]";
        accept(user, termsOnly).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LEGAL_VERSION_OUTDATED"));
        accept(user, "[]").andExpect(status().isBadRequest());

        assertThat(acceptanceCount(user)).isZero();
    }

    @Test
    void aNewVersionMustBeAcceptedAgain() throws Exception {
        User user = createUser();
        String from = issueCard(user, "AZN");
        String to = issueCard(createUser(), "AZN");
        setBalance(from, "100.00");

        String version = "test-" + UUID.randomUUID();
        Long previous = jdbc.queryForObject("SELECT max(id) FROM legal_documents WHERE type = 'TERMS' AND is_active", Long.class);
        jdbc.update("UPDATE legal_documents SET is_active = false WHERE type = 'TERMS'");
        jdbc.update("INSERT INTO legal_documents (type, version, effective_date, content, is_active) "
                + "VALUES ('TERMS', ?, CURRENT_DATE, 'New terms', true)", version);
        try {
            publishedVersionIsAskedAgain(user, from, to, version);
        } finally {
            // later test classes expect the seeded documents
            jdbc.update("UPDATE legal_documents SET is_active = false WHERE type = 'TERMS'");
            jdbc.update("UPDATE legal_documents SET is_active = true WHERE id = ?", previous);
        }
    }

    private void publishedVersionIsAskedAgain(User user, String from, String to, String version) throws Exception {

        mockMvc.perform(get("/api/auth/me").header("Authorization", bearer(user)))
                .andExpect(jsonPath("$.data.pendingLegalDocuments", hasSize(1)))
                .andExpect(jsonPath("$.data.pendingLegalDocuments[0].version").value(version));
        transfer(user, from, to).andExpect(status().isForbidden());

        // only the changed document is shown again
        accept(user, "[{\"type\":\"TERMS\",\"version\":\"" + version + "\"}]").andExpect(status().isOk());
        transfer(user, from, to).andExpect(status().isOk());
    }

    @Test
    void acceptancesCannotBeEditedOrDeleted() {
        User user = createUser();
        assertThatThrownBy(() -> jdbc.update("UPDATE user_legal_acceptances SET accepted_at = now() WHERE user_id = ?", user.getId()))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM user_legal_acceptances WHERE user_id = ?", user.getId()))
                .hasMessageContaining("append-only");
    }

    @Test
    void acceptingNeedsASignedInUser() throws Exception {
        mockMvc.perform(post("/api/legal/acceptances").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"documents\":" + activeLegalDocumentsJson() + "}"))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions accept(User user, String documentsJson) throws Exception {
        return mockMvc.perform(post("/api/legal/acceptances").header("Authorization", bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"documents\":" + documentsJson + "}"));
    }

    private ResultActions transfer(User user, String from, String to) throws Exception {
        return mockMvc.perform(post("/api/transfer").header("Authorization", bearer(user))
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fromCardNumber\":\"" + from + "\",\"toCardNumber\":\"" + to + "\",\"amount\":1.00,\"pin\":\"" + PIN + "\"}"));
    }

    private String activeVersion(String type) {
        return jdbc.queryForObject("SELECT version FROM legal_documents WHERE type = ? AND is_active", String.class, type);
    }

    private int acceptanceCount(User user) {
        return jdbc.queryForObject("SELECT count(*) FROM user_legal_acceptances WHERE user_id = ?", Integer.class, user.getId());
    }
}
