package com.example.paygenix.mapper;

import com.example.paygenix.dto.response.PaymentTransactionResponse;
import com.example.paygenix.dto.response.TransactionsResponse;
import com.example.paygenix.entity.PaymentTransaction;
import com.example.paygenix.entity.Transactions;
import com.example.paygenix.enums.Currency;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

@Mapper(componentModel = "spring",
        imports = {ZoneId.class, NumberFormat.class, Locale.class, BigDecimal.class})
public interface PaymentTransactionMapper {
    @Mapping(target = "transactionId", expression = "java(tx.getTransactionId())")
    @Mapping(target = "fromCard", expression = "java(mask(tx.getFrom_card_id()))")
    @Mapping(target = "number", expression = "java(mask(tx.getPhone_number()))")
    @Mapping(target = "amount", expression = "java(formatAmount(tx.getAmount(), tx.getCurrency()))")
    @Mapping(target = "cashbackPercent", expression = "java(formatPercent(tx.getCashbackPercent()))")
    @Mapping(target = "cashbackValue", expression = "java(formatAmount(tx.getCashbackValue(), tx.getCurrency()))")
    @Mapping(target = "createdAt", expression = "java(toBakuOffset(tx.getCreationDate()))")
    PaymentTransactionResponse toResponse(PaymentTransaction tx);

    List<PaymentTransactionResponse> toResponseList(List<PaymentTransaction> txs);

    // -------- helper methods --------

    default String mask(String value) {
        if (value == null || value.length() < 4) {
            return "****";
        }
        return "**** **** **** " + value.substring(value.length() - 4);
    }

    default String formatAmount(BigDecimal amount, Currency currency) {
        if (amount == null || currency == null) {
            return "0.00";
        }
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.US);
        nf.setMinimumFractionDigits(2);
        nf.setMaximumFractionDigits(2);
        return currency.getSymbol() + nf.format(amount);
    }

    default String formatPercent(BigDecimal percent) {
        if (percent == null) {
            return "0%";
        }
        return percent.stripTrailingZeros().toPlainString() + "%";
    }

    default OffsetDateTime toBakuOffset(Instant instant) {
        if (instant == null) {
            return null;
        }
        return instant.atZone(ZoneId.of("Asia/Baku")).toOffsetDateTime();
    }
}
