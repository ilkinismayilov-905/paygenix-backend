package com.example.paygenix.mapper;

import com.example.paygenix.dto.request.CardRequest;
import com.example.paygenix.dto.response.CardResponse;
import com.example.paygenix.entity.Cards;
import com.example.paygenix.enums.Currency;
import org.mapstruct.*;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Mapper(
        componentModel = "spring",
        imports = {
                BigDecimal.class,
                NumberFormat.class,
                Locale.class,
                YearMonth.class,
                DateTimeFormatter.class
        }
)
public interface CardMapper {

    @Mapping(
            target = "expirationDate",
            expression = "java(card.getExpirationDate() != null ? formatExpiration(card.getExpirationDate()) : null)"
    )
    @Mapping(
            target = "displayAmount",
            expression = "java(formatAmount(card.getAmount(), card.getCurrency()))"
    )
    CardResponse toResponse(Cards card);

    List<CardResponse> toResponse(List<Cards> cards);

    Cards toCards(CardRequest cardRequest);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void update(CardRequest cardRequest, @MappingTarget Cards card);

    // ---------- helpers ----------

    default String formatExpiration(String raw) {
        YearMonth ym = YearMonth.parse(raw); // 2027-03
        System.out.println("YM:" + ym);
        return ym.format(DateTimeFormatter.ofPattern("MM/yy"));
    }

    default String formatAmount(BigDecimal amount, Currency currency) {
        if (currency == null) {
            return "0.00";
        }

        NumberFormat nf = NumberFormat.getNumberInstance(Locale.US);
        nf.setMinimumFractionDigits(2);
        nf.setMaximumFractionDigits(2);

        BigDecimal safeAmount = amount != null ? amount : BigDecimal.ZERO;
        return currency.getSymbol() + nf.format(safeAmount);
    }
}
