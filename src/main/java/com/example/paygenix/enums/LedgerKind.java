package com.example.paygenix.enums;

// What a ledger posting records (V8 CHECK).
public enum LedgerKind {
    TRANSFER,
    PAYMENT,
    CASHBACK,
    TOP_UP, // card funding (Stripe, T-062); used by tests until then
    OPENING_BALANCE
}
