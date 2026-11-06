package com.example.paygenix.topup;

import java.time.Instant;

// amount as a decimal string; checkoutUrl only when the top-up is still waiting for payment
public record TopUpResponse(Long id, Long cardId, String amount, String currency, TopUpStatus status, String checkoutUrl,
                            Instant createdAt, Instant completedAt) {

    static TopUpResponse of(TopUp t) {
        return new TopUpResponse(t.getId(), t.getCardId(), t.getAmount().toPlainString(), t.getCurrency().name(), t.getStatus(),
                t.getStatus() == TopUpStatus.PENDING ? t.getCheckoutUrl() : null, t.getCreatedAt(), t.getCompletedAt());
    }
}
