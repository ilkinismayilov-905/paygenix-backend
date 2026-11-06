package com.example.paygenix.topup;

public enum TopUpStatus {
    PENDING,       // checkout open, nothing paid yet
    SUCCEEDED,     // paid and credited to the card
    EXPIRED,       // checkout closed without payment
    REFUND_NEEDED  // paid, but the card was closed meanwhile: support refunds it in Stripe
}
