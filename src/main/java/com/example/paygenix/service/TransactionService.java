package com.example.paygenix.service;

import com.example.paygenix.converter.TransactionConverter;
import com.example.paygenix.dto.response.TransactionsResponse;
import com.example.paygenix.entity.Transactions;
import com.example.paygenix.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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


    public String generateTransactionId() {
        return "TXN-" +
                LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) +
                "-" +
                UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }
}
