package com.example.paygenix.controller;

import com.example.paygenix.dto.ApiResponse;
import com.example.paygenix.dto.response.TransactionsResponse;
import com.example.paygenix.dto.response.TransferResponse;
import com.example.paygenix.service.TransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/transaction")
@CrossOrigin
@RequiredArgsConstructor
@Slf4j
public class TransactionController {
    private final TransactionService transactionService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<TransactionsResponse>>> getAllTransactions() {
        List<TransactionsResponse> list = transactionService.getAll();
        return ResponseEntity.ok(ApiResponse.success(list));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TransactionsResponse>> getTransactionById(@PathVariable Long id) {
        TransactionsResponse list = transactionService.getByID(id);
        return ResponseEntity.ok(ApiResponse.success(list));
    }

    @GetMapping("/between")
    public ResponseEntity<ApiResponse<List<TransactionsResponse>>> getBetweenDates(@RequestParam("start") @DateTimeFormat(pattern = "dd-MM-yyyy") LocalDate start,
                                                              @RequestParam("end") @DateTimeFormat(pattern = "dd-MM-yyyy") LocalDate end){
        log.info("REST request to get events between: {} and {}", start, end);

        List<TransactionsResponse> events = transactionService.getByCreationDateBetween(start, end);
       return ResponseEntity.ok(ApiResponse.success(events));
    }
}
