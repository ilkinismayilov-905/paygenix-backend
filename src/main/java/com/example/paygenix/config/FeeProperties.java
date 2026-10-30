package com.example.paygenix.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Fee rates in percent (Gate 1 decision #8). Results are rounded half-even to 2 decimals.
 * The digit limits match the columns the rates are stored in (commission_rate 5,2; cashback_percent 3,1).
 */
@Validated
@ConfigurationProperties("paygenix.fees")
public record FeeProperties(
        @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2)
        BigDecimal commissionPercent,
        @NotNull @DecimalMin("0") @DecimalMax("99.9") @Digits(integer = 2, fraction = 1)
        BigDecimal cashbackPercent
) {
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    // "0.50" must pass @Digits(fraction = 1) like "0.5" does
    public FeeProperties {
        commissionPercent = commissionPercent == null ? null : commissionPercent.stripTrailingZeros();
        cashbackPercent = cashbackPercent == null ? null : cashbackPercent.stripTrailingZeros();
    }

    public BigDecimal commissionOf(BigDecimal amount) {
        return percentOf(amount, commissionPercent);
    }

    public BigDecimal cashbackOf(BigDecimal amount) {
        return percentOf(amount, cashbackPercent);
    }

    private static BigDecimal percentOf(BigDecimal amount, BigDecimal percent) {
        return amount.multiply(percent).divide(HUNDRED, 2, RoundingMode.HALF_EVEN);
    }
}
