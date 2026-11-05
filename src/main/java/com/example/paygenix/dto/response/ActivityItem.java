package com.example.paygenix.dto.response;

import com.example.paygenix.enums.Currency;
import com.example.paygenix.enums.LedgerKind;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

// One ledger entry on one of the caller's cards.
public record ActivityItem(
        Long id,
        Long cardId,
        String last4,
        LedgerKind kind,
        @Schema(example = "\"-10.20\"", description = "Signed decimal string, two decimals: negative leaves the card, positive arrives")
        String amount,
        Currency currency,
        Instant createdAt) {
}
