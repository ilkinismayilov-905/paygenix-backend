package com.example.paygenix.phone;

import com.example.paygenix.enums.Currency;

import java.math.BigDecimal;

/**
 * The mobile operator side of "pay by phone": adds the amount to the phone's balance. Real operators plug in here;
 * MockPhoneTopUpProvider stands in until then (roadmap: no real operator integration).
 */
public interface PhoneTopUpProvider {

    /** @throws PhoneTopUpException when the operator refuses or does not answer in time; nothing was delivered then */
    void topUp(String reference, String phoneNumber, BigDecimal amount, Currency currency);
}
