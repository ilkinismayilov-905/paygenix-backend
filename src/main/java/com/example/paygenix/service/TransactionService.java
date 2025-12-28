package com.example.paygenix.service;

import com.example.paygenix.converter.TransactionConverter;
import com.example.paygenix.dto.response.TransactionsResponse;
import com.example.paygenix.entity.Transactions;
import com.example.paygenix.exceptions.NotFoundException;
import com.example.paygenix.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService {
    private final TransactionRepository transactionRepository;
    private final TransactionConverter converter;

    @Transactional(readOnly = true)
    public List<TransactionsResponse> getAll(){
        log.info("Getting all transactions");
        List<Transactions> list = transactionRepository.findAll();
        return converter.toTransactionsResponseList(list);
    }

    @Transactional(readOnly = true)
    public TransactionsResponse getByID(Long id){
        log.info("Getting transactions by ID {}", id);
        Transactions list = transactionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Transaction not found by ID"));
        log.info("Transactions found by ID {}", id);
        return converter.toTransactionsResponse(list);
    }

    @Transactional(readOnly = true)
    public List<TransactionsResponse> getByCreationDateBetween(LocalDate startDate, LocalDate endDate){
        log.info("Getting transactions between creation dates: {} and {}", startDate, endDate);

        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("Dates must not be null");
        }

        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("End date cannot be before start date");
        }

        Instant from = startDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant to   = endDate.plusDays(1)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant();


        List<Transactions> list = transactionRepository.findByCreationDateBetween(from, to)
                        .orElseThrow(() -> new NotFoundException("Transactions not found"));
        log.info("Transactions found between creation dates: {} and {}", startDate, endDate);
        System.out.println(list);
        return converter.toTransactionsResponseList(list);

    }


    public String generateTransactionId() {
        return "TXN-" +
                LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) +
                "-" +
                UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }
}
