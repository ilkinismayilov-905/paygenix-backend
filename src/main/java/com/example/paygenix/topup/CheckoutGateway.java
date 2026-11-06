package com.example.paygenix.topup;

import com.example.paygenix.enums.Currency;

import java.math.BigDecimal;

/** The payment provider's hosted checkout. One implementation talks to Stripe; tests replace it. */
public interface CheckoutGateway {

    record Checkout(String sessionId, String url) {
    }

    /**
     * Opens a checkout for the amount. The same idempotencyKey within 24 h returns the same checkout (the provider
     * deduplicates), so a retry after a lost answer never opens a second one.
     */
    Checkout open(Long userId, BigDecimal amount, Currency currency, String idempotencyKey, String successUrl, String cancelUrl);
}
