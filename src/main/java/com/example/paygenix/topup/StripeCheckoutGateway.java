package com.example.paygenix.topup;

import com.example.paygenix.enums.Currency;
import com.example.paygenix.exceptions.ApiException;
import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.param.checkout.SessionCreateParams;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Locale;

/** Stripe Checkout in test mode. Without STRIPE_SECRET_KEY top-ups answer 503 TOP_UP_UNAVAILABLE; the app still runs. */
@Component
@Slf4j
public class StripeCheckoutGateway implements CheckoutGateway {

    private final StripeClient client;

    public StripeCheckoutGateway(@Value("${stripe.secret-key:}") String secretKey) {
        this.client = secretKey.isBlank() ? null : new StripeClient(secretKey);
    }

    @Override
    public Checkout open(Long userId, BigDecimal amount, Currency currency, String idempotencyKey, String successUrl, String cancelUrl) {
        if (client == null) {
            throw unavailable();
        }
        // Only values that follow from the request: Stripe refuses a reused idempotency key with other parameters, so a
        // retry must send exactly the same ones (no "now"-based expiry; Stripe's default 24 h applies).
        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(successUrl)
                .setCancelUrl(cancelUrl)
                .setClientReferenceId(String.valueOf(userId))
                .addLineItem(SessionCreateParams.LineItem.builder()
                        .setQuantity(1L)
                        .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                .setCurrency(currency.name().toLowerCase(Locale.ROOT))
                                .setUnitAmount(amount.movePointRight(2).longValueExact())
                                .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                        .setName("Paygenix card top-up").build())
                                .build())
                        .build())
                .build();
        try {
            Session session = client.checkout().sessions().create(params,
                    RequestOptions.builder().setIdempotencyKey(idempotencyKey).build());
            return new Checkout(session.getId(), session.getUrl());
        } catch (StripeException e) {
            // class and code only: messages can carry request details
            log.warn("Stripe checkout failed: {} {}", e.getClass().getSimpleName(), e.getCode());
            throw unavailable();
        }
    }

    private static ApiException unavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "TOP_UP_UNAVAILABLE", "Top-ups are not available right now",
                Duration.ofSeconds(30));
    }
}
