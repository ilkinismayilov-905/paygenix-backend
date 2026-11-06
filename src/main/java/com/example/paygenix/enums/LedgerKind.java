package com.example.paygenix.enums;

// What a ledger posting records (V8 CHECK).
public enum LedgerKind {
    TRANSFER,
    PAYMENT,
    CASHBACK,
    TOP_UP, // card funding through Stripe Checkout (T-064, TopUpService)
    OPENING_BALANCE
}
