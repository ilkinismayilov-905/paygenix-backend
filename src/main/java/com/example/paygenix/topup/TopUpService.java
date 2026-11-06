package com.example.paygenix.topup;

import com.example.paygenix.entity.Cards;
import com.example.paygenix.entity.User;
import com.example.paygenix.enums.LedgerKind;
import com.example.paygenix.enums.SystemAccount;
import com.example.paygenix.exceptions.ApiException;
import com.example.paygenix.exceptions.IdempotencyKeyReusedException;
import com.example.paygenix.exceptions.NotFoundException;
import com.example.paygenix.repository.CardRepository;
import com.example.paygenix.service.CardService;
import com.example.paygenix.service.LedgerService;
import com.example.paygenix.service.LedgerService.Leg;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;

/**
 * Card top-ups (T-064). Starting one opens a Stripe Checkout and records it as PENDING; the money arrives only through
 * a signature-verified webhook (StripeWebhookController), which credits the card once: STRIPE_CLEARING pays, the card
 * receives. The client's answer after checkout is never trusted.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TopUpService {

    public static final BigDecimal MIN_AMOUNT = new BigDecimal("1.00");
    public static final BigDecimal MAX_AMOUNT = new BigDecimal("10000.00");

    private final TopUpRepository topUps;
    private final CardRepository cards;
    private final CardService cardService;
    private final LedgerService ledgerService;
    private final CheckoutGateway gateway;
    private final TransactionTemplate transactionTemplate;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    public record Started(Long id, String checkoutUrl) {
    }

    public Started start(User user, Long cardId, BigDecimal amount, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 255) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_IDEMPOTENCY_KEY", "Idempotency-Key header must be 1-255 characters");
        }
        if (amount.scale() > 2 || amount.compareTo(MIN_AMOUNT) < 0 || amount.compareTo(MAX_AMOUNT) > 0) {
            throw ApiException.validation("amount: must be between 1.00 and 10000.00 with at most 2 decimals");
        }
        BigDecimal value = amount.setScale(2);
        var existing = topUps.findByUserIdAndIdemKey(user.getId(), idempotencyKey);
        if (existing.isPresent()) {
            return replay(existing.get(), cardId, value);
        }
        Cards card = cardService.findOwned(user, cardId);
        // outside any transaction: the provider call must not hold a DB connection
        CheckoutGateway.Checkout checkout = gateway.open(user.getId(), value, card.getCurrency(),
                "topup-" + user.getId() + "-" + idempotencyKey,
                // Stripe fills in {CHECKOUT_SESSION_ID}; the return page asks GET /api/topups/session/{id} for the outcome
                frontendUrl + "/top-up/return?session={CHECKOUT_SESSION_ID}",
                frontendUrl + "/top-up/return?session={CHECKOUT_SESSION_ID}&cancelled=1");
        try {
            TopUp saved = topUps.saveAndFlush(new TopUp(user.getId(), card.getId(), value, card.getCurrency(), idempotencyKey,
                    checkout.sessionId(), checkout.url()));
            log.info("Top-up {} started by user {}", saved.getId(), user.getId());
            return new Started(saved.getId(), saved.getCheckoutUrl());
        } catch (DataIntegrityViolationException raced) {
            // a parallel retry with the same key saved first; the provider gave both the same checkout
            return replay(topUps.findByUserIdAndIdemKey(user.getId(), idempotencyKey).orElseThrow(() -> raced), cardId, value);
        }
    }

    private static Started replay(TopUp t, Long cardId, BigDecimal amount) {
        if (!t.getCardId().equals(cardId) || t.getAmount().compareTo(amount) != 0) {
            throw new IdempotencyKeyReusedException();
        }
        return new Started(t.getId(), t.getCheckoutUrl());
    }

    @Transactional(readOnly = true)
    public TopUp byId(User user, Long id) {
        return topUps.findById(id).filter(t -> t.getUserId().equals(user.getId()))
                .orElseThrow(() -> new NotFoundException("Top-up not found"));
    }

    @Transactional(readOnly = true)
    public TopUp bySession(User user, String sessionId) {
        return topUps.findByStripeSessionIdAndUserId(sessionId, user.getId())
                .orElseThrow(() -> new NotFoundException("Top-up not found"));
    }

    /** checkout.session.completed and paid: credit the card once. Redeliveries and unknown sessions are no-ops. */
    @Transactional
    public void paid(String sessionId, long amountTotal, String currency) {
        TopUp t = topUps.findBySessionIdForUpdate(sessionId).orElse(null);
        if (t == null) {
            log.warn("Paid checkout {} matches no top-up", sessionId);
            return;
        }
        if (t.getStatus() == TopUpStatus.SUCCEEDED || t.getStatus() == TopUpStatus.REFUND_NEEDED) {
            return; // already handled
        }
        if (amountTotal != t.getAmount().movePointRight(2).longValueExact()
                || !t.getCurrency().name().equalsIgnoreCase(currency)) {
            // never credit a different amount than the one the user asked for
            log.error("Top-up {}: paid {} {} does not match {} {}", t.getId(), amountTotal, currency, t.getAmount(), t.getCurrency());
            t.finish(TopUpStatus.REFUND_NEEDED);
            return;
        }
        Cards card = cards.findById(t.getCardId()).orElse(null); // closed cards are filtered out
        if (card == null) {
            log.warn("Top-up {} was paid for a card that is closed now; refund it", t.getId());
            t.finish(TopUpStatus.REFUND_NEEDED);
            return;
        }
        cardService.lockForUpdate(card);
        ledgerService.post(LedgerKind.TOP_UP, "topup:" + t.getId(), t.getCurrency(), List.of(
                Leg.system(SystemAccount.STRIPE_CLEARING, t.getAmount().negate()),
                Leg.card(card, t.getAmount())));
        t.finish(TopUpStatus.SUCCEEDED);
        log.info("Top-up {} credited", t.getId());
    }

    /** checkout.session.expired: nothing was paid. */
    @Transactional
    public void expired(String sessionId) {
        topUps.findBySessionIdForUpdate(sessionId)
                .filter(t -> t.getStatus() == TopUpStatus.PENDING)
                .ifPresent(t -> t.finish(TopUpStatus.EXPIRED));
    }
}
