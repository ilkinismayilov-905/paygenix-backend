package com.example.paygenix.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Issues virtual card numbers. The full number is never stored: cards are looked up by
 * HMAC-SHA256(number, server key) and shown only by their last four digits.
 */
@Service
public class PanService {

    private static final int PAN_LENGTH = 16;

    private final SecureRandom random = new SecureRandom();
    private final SecretKeySpec key;
    private final String issuerBin;

    public PanService(@Value("${card.pan-hmac-key}") String hmacKey,
                      @Value("${card.issuer-bin}") String issuerBin) {
        if (hmacKey == null || hmacKey.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("card.pan-hmac-key must be at least 32 bytes");
        }
        if (!issuerBin.matches("\\d{6}")) {
            throw new IllegalStateException("card.issuer-bin must be 6 digits");
        }
        this.key = new SecretKeySpec(hmacKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        this.issuerBin = issuerBin;
    }

    public String generate() {
        StringBuilder pan = new StringBuilder(issuerBin);
        while (pan.length() < PAN_LENGTH - 1) {
            pan.append(random.nextInt(10));
        }
        return pan.append(luhnCheckDigit(pan)).toString();
    }

    public String hmac(String pan) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            return HexFormat.of().formatHex(mac.doFinal(pan.getBytes(StandardCharsets.US_ASCII)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }

    public static String last4(String pan) {
        return pan.substring(pan.length() - 4);
    }

    public static boolean isLuhnValid(String number) {
        if (number == null || !number.matches("\\d{2,19}")) {
            return false;
        }
        return luhnCheckDigit(number.substring(0, number.length() - 1)) == number.charAt(number.length() - 1) - '0';
    }

    static int luhnCheckDigit(CharSequence payload) {
        int sum = 0;
        boolean dbl = true; // the check digit will be appended to the right, so the rightmost payload digit is doubled
        for (int i = payload.length() - 1; i >= 0; i--) {
            int d = payload.charAt(i) - '0';
            if (dbl) {
                d *= 2;
                if (d > 9) d -= 9;
            }
            sum += d;
            dbl = !dbl;
        }
        return (10 - sum % 10) % 10;
    }
}
