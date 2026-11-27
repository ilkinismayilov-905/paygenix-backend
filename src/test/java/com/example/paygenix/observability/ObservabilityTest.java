package com.example.paygenix.observability;

import com.example.paygenix.enums.LedgerKind;
import com.example.paygenix.enums.SystemAccount;
import com.example.paygenix.service.LedgerService.Leg;
import com.example.paygenix.support.IntegrationTest;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// M9: a request id in every log line and response, and business counters for logins and ledger postings.
@ExtendWith(OutputCaptureExtension.class)
class ObservabilityTest extends IntegrationTest {

    @Autowired MeterRegistry meterRegistry;

    @Test
    void requestIdFromTheProxyIsLoggedAndEchoed(CapturedOutput output) throws Exception {
        String id = "0123456789abcdef0123456789abcdef";
        mockMvc.perform(post("/api/v1/auth/login").header("X-Request-Id", id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody-" + System.nanoTime() + "@example.test\",\"password\":\"x-password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Request-Id", id));
        assertThat(output.getOut()).contains("[" + id + "]").contains("Login request received");
    }

    @Test
    void malformedRequestIdIsReplaced() throws Exception {
        String sent = "bad id\r\nX-Injected: 1";
        String echoed = mockMvc.perform(get("/api/v1/legal/terms").header("X-Request-Id", sent))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("X-Request-Id");
        assertThat(echoed).isNotEqualTo(sent).matches("[0-9a-f-]{36}");
    }

    @Test
    void requestWithoutIdGetsOne() throws Exception {
        mockMvc.perform(get("/api/v1/legal/terms"))
                .andExpect(header().string("X-Request-Id", org.hamcrest.Matchers.matchesPattern("[0-9a-f-]{36}")));
    }

    @Test
    void loginOutcomesAreCounted() throws Exception {
        var user = createUser();
        double success = count("paygenix.auth.logins", "outcome", "success");
        double bad = count("paygenix.auth.logins", "outcome", "bad_credentials");
        login(user.getEmail(), "wrong-password");
        login(user.getEmail(), PASSWORD);
        assertThat(count("paygenix.auth.logins", "outcome", "bad_credentials")).isEqualTo(bad + 1);
        assertThat(count("paygenix.auth.logins", "outcome", "success")).isEqualTo(success + 1);
    }

    @Test
    void onlyCommittedPostingsAreCounted() throws Exception {
        String pan = issueCard(createUser(), "AZN");
        double before = count("paygenix.ledger.postings", "kind", "TOP_UP");
        setBalance(pan, "10.00");
        assertThat(count("paygenix.ledger.postings", "kind", "TOP_UP")).isEqualTo(before + 1);

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(tx -> {
            var card = cardRepository.findById(cardOf(pan).getId()).orElseThrow();
            ledgerService.post(LedgerKind.TOP_UP, "rolled-back", card.getCurrency(), List.of(
                    Leg.system(SystemAccount.STRIPE_CLEARING, new BigDecimal("-1.00")),
                    Leg.card(card, new BigDecimal("1.00"))));
            throw new IllegalStateException("roll back");
        })).hasMessage("roll back");
        assertThat(count("paygenix.ledger.postings", "kind", "TOP_UP")).isEqualTo(before + 1);
    }

    private void login(String email, String password) throws Exception {
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }

    private double count(String name, String tag, String value) {
        Counter counter = meterRegistry.find(name).tag(tag, value).counter();
        return counter == null ? 0 : counter.count();
    }
}
