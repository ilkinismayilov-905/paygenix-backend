package com.example.paygenix.dto.request;

import com.example.paygenix.enums.CardNetwork;
import com.example.paygenix.enums.Currency;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CardRequest(
        String cardHolder,
        @NotBlank(message = "Card number cannot be blank")
        @Pattern(
                regexp = "\\d{16}",
                message = "Card number must be exactly 16 digits"
        )
        String cardNumber,
        String expirationDate,
        @NotBlank(message = "CVV cannot be blank")
        @Pattern(
                regexp = "\\d{3}",
                message = "CVV must be exactly 3 digits"
        )
        String cvv,
        CardNetwork cardNetwork,
        Currency currency
) {
}
