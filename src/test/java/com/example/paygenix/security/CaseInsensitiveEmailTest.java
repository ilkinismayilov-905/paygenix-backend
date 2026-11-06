package com.example.paygenix.security;

import com.example.paygenix.dto.request.EmailRequest;
import com.example.paygenix.entity.User;
import com.example.paygenix.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// T-080: Victim@x and victim@x are the same account.
class CaseInsensitiveEmailTest extends IntegrationTest {

    @Test
    void loginIgnoresEmailCase() throws Exception {
        User user = createUser();
        String shouted = "  " + user.getEmail().toUpperCase(Locale.ROOT) + " ";

        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + shouted + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void requestEmailIsNormalizedWithoutLocaleSurprises() {
        Locale previous = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("az"));
        try {
            assertThat(new EmailRequest(" Ilkin@Example.TEST ").email()).isEqualTo("ilkin@example.test");
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void databaseRejectsACaseOnlyTwin() {
        User user = createUser();

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO users (id, email, fullname, pin_hash, username) SELECT max(id) + 1, ?, 'Twin', 'x', ? FROM users",
                user.getEmail().toUpperCase(Locale.ROOT), user.getUsername() + "-twin"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("users_email_lower_key");
    }
}
