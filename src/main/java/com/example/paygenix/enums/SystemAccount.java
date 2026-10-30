package com.example.paygenix.enums;

// Ledger accounts that belong to Paygenix itself; one per currency (V7).
public enum SystemAccount {
    FEE_REVENUE,
    CASHBACK_EXPENSE,
    STRIPE_CLEARING,
    PAYMENT_PAYOUT,
    OPENING_BALANCE
}
