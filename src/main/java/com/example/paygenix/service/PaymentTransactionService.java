package com.example.paygenix.service;

import com.example.paygenix.dto.response.PaymentTransactionResponse;
import com.example.paygenix.entity.PaymentTransaction;
import com.example.paygenix.mapper.PaymentTransactionMapper;
import com.example.paygenix.repository.PaymentTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentTransactionService {
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final PaymentTransactionMapper paymentTransactionMapper;

    public String generatePaymentTransactionId() {
        return "PTXN-" +
                LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) +
                "-" +
                UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

    @Transactional(readOnly = true)
    public List<PaymentTransactionResponse> getHistory(){
        log.info("Getting history");
        List<PaymentTransaction> paymentTransactions = paymentTransactionRepository.findAll();
        return paymentTransactionMapper.toResponseList(paymentTransactions);
    }
}
