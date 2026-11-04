package com.example.paygenix.support;

import com.example.paygenix.entity.Cards;
import com.example.paygenix.entity.User;
import com.example.paygenix.enums.LedgerKind;
import com.example.paygenix.enums.Role;
import com.example.paygenix.enums.SystemAccount;
import com.example.paygenix.repository.CardRepository;
import com.example.paygenix.repository.UserRepository;
import com.example.paygenix.security.RefreshCookie;
import com.example.paygenix.service.JwtService;
import com.example.paygenix.service.LedgerService;
import com.example.paygenix.service.LedgerService.Leg;
import com.example.paygenix.service.PanService;
import com.example.paygenix.service.SessionService;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Base for integration tests: real Postgres and Redis in containers, MockMvc, and user/token helpers. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTest {

    public static final String PIN = "123456";
    public static final String PASSWORD = "correct-horse-battery";

    @Autowired protected MockMvc mockMvc;
    @Autowired protected UserRepository userRepository;
    @Autowired protected PasswordEncoder passwordEncoder;
    @Autowired protected JwtService jwtService;
    @Autowired protected SessionService sessionService;
    @Autowired protected CardRepository cardRepository;
    @Autowired protected PanService panService;
    @Autowired protected LedgerService ledgerService;
    @Autowired protected TransactionTemplate transactionTemplate;
    @Autowired protected JdbcTemplate jdbc;

    protected static final String ALLOWED_ORIGIN = "http://localhost:3000";

    /** A browser refresh: the token as the refresh cookie, from the frontend's origin. */
    protected ResultActions cookieRefresh(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/auth/refresh-token").header("Origin", ALLOWED_ORIGIN)
                .cookie(new Cookie(RefreshCookie.NAME, refreshToken)));
    }

    /** The refresh token a response set as the cookie. */
    protected static String refreshCookieOf(MockHttpServletResponse res) {
        String header = res.getHeader("Set-Cookie");
        assertThat(header).startsWith(RefreshCookie.NAME + "=");
        return header.substring(header.indexOf('=') + 1, header.indexOf(';'));
    }

    protected User createUser(Role... roles) {
        String id = UUID.randomUUID().toString().substring(0, 8);
        return userRepository.save(User.builder()
                .email("user-" + id + "@example.test")
                .username("user" + id)
                .fullname("Test User " + id)
                .pinHash(passwordEncoder.encode(PIN))
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .roles(Set.of(roles.length == 0 ? new Role[]{Role.USER_ROLE} : roles))
                .status(User.Status.ACTIVE)
                .build());
    }

    protected String bearer(User user) {
        return "Bearer " + sessionService.start(user, "test").token();
    }

    /** Issues a card through the API and returns the full number (only ever visible in that response). */
    protected String issueCard(User owner, String currency) throws Exception {
        String body = mockMvc.perform(post("/api/cards").header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currency\":\"" + currency + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.cardNumber");
    }

    protected Cards cardOf(String pan) {
        return cardRepository.findByPanHmac(panService.hmac(pan)).orElseThrow();
    }

    protected void setBalance(String pan, String amount) {
        setBalance(cardOf(pan), amount);
    }

    /** Brings the card to the given balance with a simulated top-up booked against STRIPE_CLEARING. */
    protected void setBalance(Cards card, String amount) {
        transactionTemplate.executeWithoutResult(tx -> {
            Cards managed = cardRepository.findById(card.getId()).orElseThrow();
            BigDecimal delta = new BigDecimal(amount).subtract(managed.getAmount());
            ledgerService.post(LedgerKind.TOP_UP, "test", managed.getCurrency(), List.of(
                    Leg.system(SystemAccount.STRIPE_CLEARING, delta.negate()),
                    Leg.card(managed, delta)));
        });
    }

    // Checked across the whole database, so every other test's postings are covered too.
    protected void assertBooksBalanced() {
        assertThat(jdbc.queryForList(
                "SELECT tx_id FROM ledger_entries GROUP BY tx_id HAVING SUM(amount) <> 0 OR COUNT(DISTINCT currency) > 1"))
                .isEmpty();
        for (Map<String, Object> row : jdbc.queryForList("SELECT currency, SUM(amount) AS total FROM ledger_entries GROUP BY currency")) {
            assertThat((BigDecimal) row.get("total")).as("sum of %s entries", row.get("currency")).isZero();
        }
        assertThat(jdbc.queryForList("""
                SELECT c.id FROM cards c LEFT JOIN ledger_accounts a ON a.card_id = c.id
                LEFT JOIN ledger_entries e ON e.account_id = a.id
                GROUP BY c.id, c.amount HAVING c.amount <> COALESCE(SUM(e.amount), 0)""")).isEmpty();
    }

    protected BigDecimal balanceOf(String pan) {
        return cardOf(pan).getAmount();
    }
}
