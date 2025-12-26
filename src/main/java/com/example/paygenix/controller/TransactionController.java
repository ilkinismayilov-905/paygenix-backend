package com.example.paygenix.controller;

import com.example.paygenix.dto.response.TransactionsResponse;
import com.example.paygenix.dto.response.TransferResponse;
import com.example.paygenix.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/transaction")
@CrossOrigin
@RequiredArgsConstructor
public class TransactionController {
    private final TransactionService transactionService;

    @GetMapping
    public ResponseEntity<Map<String,Object>> getAllTransfers() {
        Map<String,Object> response = new HashMap<>();
        try {
            List<TransactionsResponse> list = transactionService.getAll();
            response.put("success", true);
            response.put("data", list);
            return ResponseEntity.status(HttpStatus.OK).body(response);
        }catch (IllegalArgumentException e){
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
        catch (Exception e) {

            response.put("success", false);
            response.put("message", e.getMessage());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}
