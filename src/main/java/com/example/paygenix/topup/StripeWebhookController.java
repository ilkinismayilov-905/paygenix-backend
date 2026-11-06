package com.example.paygenix.topup;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.net.Webhook;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Stripe's webhook (T-064): the only way money reaches a card from outside. The raw body must carry a valid
 * Stripe-Signature for STRIPE_WEBHOOK_SECRET (5-minute tolerance against replays); anything else is refused. Handling
 * is idempotent, so Stripe's redeliveries are harmless. Public: Stripe has no token, the signature is the proof.
 */
@RestController
@Slf4j
@Hidden
public class StripeWebhookController {

    static final long TOLERANCE_SECONDS = 300;

    private final TopUpService topUpService;
    private final JsonMapper json;
    private final String secret;

    public StripeWebhookController(TopUpService topUpService, JsonMapper json, @Value("${stripe.webhook-secret:}") String secret) {
        this.topUpService = topUpService;
        this.json = json;
        this.secret = secret;
    }

    @PostMapping("/api/v1/stripe/webhook")
    public ResponseEntity<Void> receive(@RequestBody String payload,
                                        @RequestHeader(value = "Stripe-Signature", required = false) String signature) {
        if (secret.isBlank()) {
            return ResponseEntity.status(503).build(); // not configured: Stripe retries later
        }
        try {
            Webhook.Signature.verifyHeader(payload, signature == null ? "" : signature, secret, TOLERANCE_SECONDS);
        } catch (SignatureVerificationException | RuntimeException e) { // a malformed header (t=abc) throws NumberFormatException
            log.warn("Stripe webhook with a bad signature refused");
            return ResponseEntity.badRequest().build();
        }
        JsonNode event = json.readTree(payload);
        String type = event.path("type").asString("");
        JsonNode session = event.path("data").path("object");
        String sessionId = session.path("id").asString("");
        switch (type) {
            case "checkout.session.completed", "checkout.session.async_payment_succeeded" -> {
                // "completed" can arrive before a delayed payment method settles; only "paid" moves money
                if ("paid".equals(session.path("payment_status").asString(""))) {
                    topUpService.paid(sessionId, session.path("amount_total").asLong(-1), session.path("currency").asString(""));
                }
            }
            case "checkout.session.expired", "checkout.session.async_payment_failed" -> topUpService.expired(sessionId);
            default -> log.debug("Stripe event {} ignored", type);
        }
        return ResponseEntity.ok().build();
    }
}
