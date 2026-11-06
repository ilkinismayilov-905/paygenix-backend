package com.example.paygenix.phone;

import com.example.paygenix.enums.Currency;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Stand-in operator with predictable outcomes, so every path can be tried from the UI:
 * a number ending in 000 is refused, one ending in 999 times out, every other number succeeds.
 */
@Component
@Slf4j
public class MockPhoneTopUpProvider implements PhoneTopUpProvider {

    @Override
    public void topUp(String reference, String phoneNumber, BigDecimal amount, Currency currency) {
        if (phoneNumber.endsWith("000")) {
            log.info("Mock operator refused top-up {}", reference);
            throw PhoneTopUpException.rejected();
        }
        if (phoneNumber.endsWith("999")) {
            log.info("Mock operator timed out on top-up {}", reference);
            throw PhoneTopUpException.timeout();
        }
        log.info("Mock operator delivered top-up {}", reference);
    }
}
