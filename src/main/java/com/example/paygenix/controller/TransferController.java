package com.example.paygenix.controller;

import com.example.paygenix.dto.ApiResponse;
import com.example.paygenix.dto.request.TransferRequest;
import com.example.paygenix.dto.response.TransferResponse;

import com.example.paygenix.service.TransferService;
import lombok.RequiredArgsConstructor;
import org.checkerframework.checker.units.qual.A;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.List;

@RestController
@RequestMapping("/api/transfer")
@CrossOrigin
@RequiredArgsConstructor
public class TransferController {
    private final TransferService transferService;

    @PostMapping
    public ResponseEntity<ApiResponse<TransferResponse>> createTransfer(@RequestBody TransferRequest transferRequest) {
        TransferResponse transfer = transferService.transfer(transferRequest);
       return ResponseEntity.ok(ApiResponse.success(transfer));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<TransferResponse>>> getTransfers() {
        List<TransferResponse> list = transferService.getAllTransfers();
        return ResponseEntity.ok(ApiResponse.success(list));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TransferResponse>> getTransfersById(@PathVariable Long id) {
        TransferResponse list = transferService.getTransferById(id);
        return ResponseEntity.ok(ApiResponse.success(list));
    }
}
