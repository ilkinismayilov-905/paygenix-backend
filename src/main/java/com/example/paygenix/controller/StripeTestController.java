package com.example.paygenix.controller;

import com.example.paygenix.service.StripeTestPaymentService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.net.Webhook;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/stripe/test")
@RequiredArgsConstructor
public class StripeTestController {

    @Value("${stripe.webhook-secret}")
    private String webhookSecret;

    private final StripeTestPaymentService stripeTestPaymentService;

    @PostMapping("/pay")
    public Map<String, Object> testPayment() throws StripeException {

        PaymentIntent intent = stripeTestPaymentService.testPayment();

        return Map.of(
                "id", intent.getId(),
                "status", intent.getStatus(),
                "amount", intent.getAmount(),
                "currency", intent.getCurrency()
        );
    }

    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sig
    ) throws SignatureVerificationException {
        Event event = Webhook.constructEvent(
                payload, sig, webhookSecret
        );

        if ("payment_intent.succeeded".equals(event.getType())) {
            // DB: payment = SUCCESS
        }

        if ("payment_intent.payment_failed".equals(event.getType())) {
            // DB: payment = FAILED
        }

        return ResponseEntity.ok("");
    }

}

