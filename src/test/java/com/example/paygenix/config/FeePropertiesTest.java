package com.example.paygenix.config;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

// T-061: fees are a configured percentage of the amount, rounded half-even to 2 decimals.
class FeePropertiesTest {

    private final FeeProperties fees = new FeeProperties(new BigDecimal("2.00"), new BigDecimal("0.5"));

    @Test
    void commissionRoundsHalfEven() {
        assertThat(fees.commissionOf(new BigDecimal("10.00"))).isEqualByComparingTo("0.20");
        assertThat(fees.commissionOf(new BigDecimal("0.25"))).isEqualByComparingTo("0.00"); // 0.005 -> even 0.00
        assertThat(fees.commissionOf(new BigDecimal("0.75"))).isEqualByComparingTo("0.02"); // 0.015 -> even 0.02
        assertThat(fees.commissionOf(new BigDecimal("50000.00"))).isEqualByComparingTo("1000.00");
    }

    @Test
    void cashbackRoundsHalfEven() {
        assertThat(fees.cashbackOf(new BigDecimal("10.00"))).isEqualByComparingTo("0.05");
        assertThat(fees.cashbackOf(new BigDecimal("1.99"))).isEqualByComparingTo("0.01"); // 0.00995
        assertThat(fees.cashbackOf(new BigDecimal("0.99"))).isEqualByComparingTo("0.00"); // 0.00495
        assertThat(fees.cashbackOf(new BigDecimal("3.00"))).isEqualByComparingTo("0.02"); // 0.015 -> even 0.02
    }

    @Test
    void resultsAlwaysHaveTwoDecimals() {
        assertThat(fees.commissionOf(new BigDecimal("7")).scale()).isEqualTo(2);
        assertThat(fees.cashbackOf(new BigDecimal("7")).scale()).isEqualTo(2);
    }
}
