package com.example.paygenix.phone;

import com.example.paygenix.enums.Currency;

import java.math.BigDecimal;

/**
 * The mobile operator side of "pay by phone": adds the amount to the phone's balance. Real operators plug in here;
 * MockPhoneTopUpProvider stands in until then (roadmap: no real operator integration).
 * <p>
 * Contract (T-144). PaymentService calls it after the amount is already held and the transaction committed, so it may
 * be slow, but an adapter must give up well within {@link com.example.paygenix.service.PaymentReconciliation#RECONCILE_AFTER}
 * and answer UNKNOWN rather than throw. The reference is the idempotency key: a second topUp with the same reference
 * must not deliver twice, it reports the first outcome.
 */
public interface PhoneTopUpProvider {

    OperatorResult topUp(String reference, String phoneNumber, BigDecimal amount, Currency currency);

    /** The operator's record of an earlier topUp: DELIVERED, NOT_DELIVERED (refused or never received) or UNKNOWN. */
    OperatorResult status(String reference);
}
