package com.example.paygenix.phone;

import com.example.paygenix.enums.Currency;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stand-in operator with predictable outcomes, so every path can be tried from the UI:
 * <ul>
 * <li>a number ending in 000 is refused;</li>
 * <li>one ending in 999 never answers and never delivers (reconciliation returns the money);</li>
 * <li>one ending in 998 delivers but its answer is lost (a retry, or reconciliation, completes the payment);</li>
 * <li>every other number is delivered.</li>
 * </ul>
 * Outcomes are remembered per reference, like a real operator's records, but only in memory: after a restart a
 * reference it has forgotten reads as NOT_DELIVERED.
 */
@Component
@Slf4j
public class MockPhoneTopUpProvider implements PhoneTopUpProvider {

    private final Map<String, OperatorResult> records = new ConcurrentHashMap<>();

    @Override
    public OperatorResult topUp(String reference, String phoneNumber, BigDecimal amount, Currency currency) {
        OperatorResult earlier = records.get(reference);
        if (earlier != null) {
            log.info("Mock operator already has top-up {}: {}", reference, earlier);
            return earlier;
        }
        if (phoneNumber.endsWith("000")) {
            log.info("Mock operator refused top-up {}", reference);
            records.put(reference, OperatorResult.NOT_DELIVERED);
            return OperatorResult.NOT_DELIVERED;
        }
        if (phoneNumber.endsWith("999")) {
            log.info("Mock operator timed out on top-up {}", reference);
            return OperatorResult.UNKNOWN;
        }
        records.put(reference, OperatorResult.DELIVERED);
        if (phoneNumber.endsWith("998")) {
            log.info("Mock operator delivered top-up {} but the answer was lost", reference);
            return OperatorResult.UNKNOWN;
        }
        log.info("Mock operator delivered top-up {}", reference);
        return OperatorResult.DELIVERED;
    }

    @Override
    public OperatorResult status(String reference) {
        return records.getOrDefault(reference, OperatorResult.NOT_DELIVERED);
    }
}
