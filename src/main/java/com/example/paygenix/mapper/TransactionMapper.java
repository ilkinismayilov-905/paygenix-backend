package com.example.paygenix.mapper;

import com.example.paygenix.dto.response.TransactionsResponse;
import com.example.paygenix.entity.Transactions;
import com.example.paygenix.enums.Currency;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

@Mapper(componentModel = "spring",
        imports = {ZoneId.class, NumberFormat.class, Locale.class, BigDecimal.class}
)
public interface TransactionMapper {

    @Mapping(
            target = "fromCard",
            expression = "java(mask(transactions.getFrom_card_id()))"
    )
    @Mapping(
            target = "toCard",
            expression = "java(mask(transactions.getTo_card_id()))"
    )
    @Mapping(
            target = "amount",
            expression = "java(formatAmount(transactions.getAmount(), transactions.getCurrency()))"
    )
    @Mapping(
            target = "commissionRate",
            expression = "java(formatCommission(transactions.getCommissionRate()))"
    )
    @Mapping(
            target = "commissionValue",
            expression = "java(formatAmount(transactions.getCommissionValue(),transactions.getCurrency()))"
    )
    @Mapping(
            target = "totalAmount",
            expression = "java(formatAmount(total(transactions), transactions.getCurrency()))"
    )
    @Mapping(
            target = "createdAt",
            expression = "java(transactions.getCreationDate().atZone(ZoneId.of(\"Asia/Baku\")).toOffsetDateTime())"
    )
    TransactionsResponse toResponse(Transactions transactions);

    List<TransactionsResponse> toResponseList(List<Transactions> transactionsList);


    default BigDecimal total(Transactions tx) {
        return tx.getAmount().add(tx.getCommissionValue());
    }

    default String formatCommission(BigDecimal rate) {
        return rate.stripTrailingZeros().toPlainString() + "%";
    }

    default String mask(String cardNumber) {
        return "**** **** **** " + cardNumber.substring(cardNumber.length() - 4);
    }

    default String formatAmount(BigDecimal amount, Currency currency) {
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.US);
        nf.setMinimumFractionDigits(2);
        nf.setMaximumFractionDigits(2);

        BigDecimal safeAmount = amount != null ? amount : BigDecimal.ZERO;
        return currency.getSymbol() + nf.format(safeAmount);
    }
}
