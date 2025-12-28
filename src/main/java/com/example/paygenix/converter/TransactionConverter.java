package com.example.paygenix.converter;

import com.example.paygenix.dto.response.TransactionsResponse;
import com.example.paygenix.entity.Transactions;
import com.example.paygenix.enums.Currency;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Component
public class TransactionConverter {

    public TransactionsResponse toTransactionsResponse(Transactions transactions){
        BigDecimal total =
                transactions.getAmount().add(transactions.getCommissionValue());

        OffsetDateTime createdAt =
                transactions.getCreationDate()   // Instant
                        .atZone(ZoneId.of("Asia/Baku"))
                        .toOffsetDateTime();

        return new TransactionsResponse(
                transactions.getTransactionId(),
                mask(transactions.getFrom_card_id()),
                mask(transactions.getTo_card_id()),
                formatAmount(transactions.getAmount(), transactions.getCurrency()),
                formatCommission(transactions.getCommissionRate()),
                formatAmount(transactions.getCommissionValue(),transactions.getCurrency()),
                formatAmount(total, transactions.getCurrency()),
                transactions.getStatus(),
                createdAt);
    }

    public List<TransactionsResponse> toTransactionsResponseList(List<Transactions> transactions){
        return transactions.stream()
                .map(this::toTransactionsResponse)
                .collect(Collectors.toList());
    }

    private String formatCommission(BigDecimal rate) {
        return rate.stripTrailingZeros().toPlainString() + "%";
    }

    private String mask(String cardNumber) {
        return "**** **** **** " +
                cardNumber.substring(cardNumber.length() - 4);
    }

    private String formatAmount(BigDecimal amount, Currency currency) {
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.US);
        nf.setMinimumFractionDigits(2);
        nf.setMaximumFractionDigits(2);

        BigDecimal safeAmount = (amount != null) ? amount : BigDecimal.ZERO;

        return currency.getSymbol() + nf.format(safeAmount);
    }


}
