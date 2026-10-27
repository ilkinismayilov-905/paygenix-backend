package com.example.paygenix.dto.request;

import com.example.paygenix.enums.Currency;
import jakarta.validation.constraints.NotNull;

// The system issues the number, expiry and holder name; the user only picks the currency.
public record CardRequest(
        @NotNull(message = "Currency is required")
        Currency currency
) {
}
