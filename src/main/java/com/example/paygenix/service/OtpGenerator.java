package com.example.paygenix.service;

import java.security.SecureRandom;

// SEC-17: OTPs come from a CSPRNG and cover the whole 000000-999999 range.
public final class OtpGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    private OtpGenerator() {}

    public static String newOtp() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }
}
