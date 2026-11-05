package com.example.paygenix.mapper;

import com.example.paygenix.dto.response.CardResponse;
import com.example.paygenix.entity.Cards;
import com.example.paygenix.enums.Currency;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Component
public class CardConverter {

    public CardResponse toResponse(Cards card) {
        return new CardResponse(
                card.getId(),
                "**** **** **** " + card.getLast4(),
                card.getExpirationDate() != null
                        ? formatExpiration(card.getExpirationDate())
                        : null,
                card.getCardHolderName(),
                card.getCardNetwork(),
                card.getCurrency(),
                balance(card).toPlainString(),
                formatAmount(balance(card), card.getCurrency()) // $1,300.00
        );
    }

    private static BigDecimal balance(Cards card) {
        return (card.getAmount() != null ? card.getAmount() : BigDecimal.ZERO).setScale(2, RoundingMode.UNNECESSARY);
    }

    public List<CardResponse> toResponse(List<Cards> cards) {
        return cards.stream()
                .map(this::toResponse)
                .toList();
    }

    private static String formatExpiration(String raw) {
        YearMonth ym = YearMonth.parse(raw); // 2027-03
        return ym.format(DateTimeFormatter.ofPattern("MM/yy"));
    }

    private static String formatAmount(BigDecimal amount, Currency currency) {
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.US);
        nf.setMinimumFractionDigits(2);
        nf.setMaximumFractionDigits(2);
        return currency.getSymbol() + nf.format(amount);
    }
}
