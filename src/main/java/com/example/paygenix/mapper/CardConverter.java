package com.example.paygenix.mapper;

import com.example.paygenix.dto.request.CardRequest;
import com.example.paygenix.dto.response.CardResponse;
import com.example.paygenix.entity.Cards;
import com.example.paygenix.enums.Currency;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import static org.hibernate.Hibernate.map;

@Component
public class CardConverter {

    public CardResponse toResponse(Cards card) {
        return new CardResponse(
                card.getId(),
                card.getCardNumber(),
                card.getExpirationDate() != null
                        ? formatExpiration(card.getExpirationDate())
                        : null,
                card.getCardHolderName(),
                card.getCardNetwork(),
                formatAmount(
                        card.getAmount() != null ? card.getAmount() : BigDecimal.ZERO,
                        card.getCurrency()
                ) // $1,300.00
        );
    }

    public List<CardResponse> toResponse(List<Cards> cards) {
        return cards.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public Cards toCards(CardRequest cardRequest) {
        return new Cards(
                cardRequest.cardNumber(),
                cardRequest.expirationDate(),
                cardRequest.cardHolder(),
                cardRequest.cvv(),
                cardRequest.cardNetwork(),
                cardRequest.currency()
        );
    }

    public void update(CardRequest cardRequest, Cards card) {
        if (card == null || cardRequest == null) {
            return;
        }

        if (cardRequest.cardHolder() != null) card.setCardHolderName(cardRequest.cardHolder());
        if (cardRequest.cardNumber() != null) card.setCardNumber(cardRequest.cardNumber());
        if (cardRequest.expirationDate() != null) card.setExpirationDate(cardRequest.expirationDate());
        if (cardRequest.cvv() != null) card.setCvv(cardRequest.cvv());
        if (cardRequest.cardNetwork() != null) card.setCardNetwork(cardRequest.cardNetwork());
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
