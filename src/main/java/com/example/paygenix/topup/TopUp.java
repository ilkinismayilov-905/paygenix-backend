package com.example.paygenix.topup;

import com.example.paygenix.enums.Currency;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "top_ups")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TopUp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "card_id", nullable = false, updatable = false)
    private Long cardId;

    @Column(nullable = false, updatable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Currency currency;

    @Column(name = "idem_key", nullable = false, updatable = false)
    private String idemKey;

    @Column(name = "stripe_session_id", nullable = false, updatable = false)
    private String stripeSessionId;

    @Column(name = "checkout_url", nullable = false, updatable = false)
    private String checkoutUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TopUpStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    public TopUp(Long userId, Long cardId, BigDecimal amount, Currency currency, String idemKey, String stripeSessionId,
                 String checkoutUrl) {
        this.userId = userId;
        this.cardId = cardId;
        this.amount = amount;
        this.currency = currency;
        this.idemKey = idemKey;
        this.stripeSessionId = stripeSessionId;
        this.checkoutUrl = checkoutUrl;
        this.status = TopUpStatus.PENDING;
        this.createdAt = Instant.now();
    }

    void finish(TopUpStatus result) {
        status = result;
        completedAt = Instant.now();
    }
}
