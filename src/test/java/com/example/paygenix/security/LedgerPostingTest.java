package com.example.paygenix.security;

import com.example.paygenix.entity.Cards;
import com.example.paygenix.enums.Currency;
import com.example.paygenix.enums.LedgerKind;
import com.example.paygenix.enums.SystemAccount;
import com.example.paygenix.service.LedgerService.Leg;
import com.example.paygenix.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// T-060: LedgerService.post books only balanced, single-currency postings; a rejected posting changes nothing.
class LedgerPostingTest extends IntegrationTest {

    @Test
    void unbalancedPostingIsRejected() throws Exception {
        Cards card = cardOf(issueCard(createUser(), "AZN"));
        assertRejected(Currency.AZN, List.of(Leg.card(card, amount("10.00")),
                Leg.system(SystemAccount.STRIPE_CLEARING, amount("-9.99"))), "does not balance");
        assertThat(cardRepository.findById(card.getId()).orElseThrow().getAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    void cardInAnotherCurrencyIsRejected() throws Exception {
        Cards usd = cardOf(issueCard(createUser(), "USD"));
        assertRejected(Currency.AZN, List.of(Leg.card(usd, amount("10.00")),
                Leg.system(SystemAccount.STRIPE_CLEARING, amount("-10.00"))), "one currency");
    }

    @Test
    void moreThanTwoDecimalsIsRejected() throws Exception {
        Cards card = cardOf(issueCard(createUser(), "AZN"));
        assertRejected(Currency.AZN, List.of(Leg.card(card, amount("0.005")),
                Leg.system(SystemAccount.STRIPE_CLEARING, amount("-0.005"))), "2 decimals");
    }

    @Test
    void singleNonZeroLegIsRejected() throws Exception {
        Cards card = cardOf(issueCard(createUser(), "AZN"));
        assertRejected(Currency.AZN, List.of(Leg.card(card, amount("5.00")),
                Leg.system(SystemAccount.STRIPE_CLEARING, BigDecimal.ZERO)), "at least two legs");
    }

    @Test
    void allZeroPostingBooksNothing() throws Exception {
        Cards card = cardOf(issueCard(createUser(), "AZN"));
        Object txId = transactionTemplate.execute(tx -> ledgerService.post(LedgerKind.TOP_UP, "test", Currency.AZN, List.of(
                Leg.card(cardRepository.findById(card.getId()).orElseThrow(), BigDecimal.ZERO),
                Leg.system(SystemAccount.STRIPE_CLEARING, BigDecimal.ZERO))));
        assertThat(txId).isNull();
        assertThat(ledgerService.balanceOf(card)).isEqualByComparingTo("0.00");
    }

    @Test
    void ledgerKindEnumMatchesTheDatabaseCheck() throws Exception {
        Long account = jdbc.queryForObject("SELECT id FROM ledger_accounts WHERE code = 'STRIPE_CLEARING' AND currency = 'AZN'", Long.class);
        String insert = "INSERT INTO ledger_entries (tx_id, account_id, amount, currency, kind, reference) "
                + "VALUES (gen_random_uuid(), ?, 1, 'AZN', ?, 'check')";
        // inside a rolled-back transaction: the rows are unbalanced on purpose and entries cannot be deleted
        transactionTemplate.executeWithoutResult(tx -> {
            for (LedgerKind kind : LedgerKind.values()) {
                jdbc.update(insert, account, kind.name());
            }
            tx.setRollbackOnly();
        });
        assertThatThrownBy(() -> jdbc.update(insert, account, "BOGUS")).isInstanceOf(DataIntegrityViolationException.class);
    }

    private void assertRejected(Currency currency, List<Leg> legs, String message) {
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(tx ->
                ledgerService.post(LedgerKind.TOP_UP, "test", currency, legs)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(message);
    }

    private static BigDecimal amount(String value) {
        return new BigDecimal(value);
    }
}
