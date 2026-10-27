package com.example.paygenix.controller;

import com.example.paygenix.dto.ApiResponseData;
import org.springframework.security.access.prepost.PreAuthorize;
import com.example.paygenix.dto.response.TransactionsResponse;
import com.example.paygenix.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/transaction")
@CrossOrigin
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Transaction Management", description = "APIs for viewing and filtering financial transaction history")
@SecurityRequirement(name = "bearerAuth")
public class TransactionController {

    private final TransactionService transactionService;

    @Operation(summary = "Get all transactions", description = "Retrieves a complete list of all transactions for the current user.")
    @ApiResponse(responseCode = "200", description = "Transaction list retrieved successfully")
    @PreAuthorize("hasRole('ADMIN_ROLE')")
    @GetMapping
    public ResponseEntity<ApiResponseData<List<TransactionsResponse>>> getAllTransactions() {
        log.info("Fetching all transactions");
        List<TransactionsResponse> list = transactionService.getAll();
        return ResponseEntity.ok(ApiResponseData.success(list));
    }

    @Operation(summary = "Get transaction by ID", description = "Retrieves details of a specific transaction using its unique ID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Transaction details found"),
            @ApiResponse(responseCode = "404", description = "Transaction not found")
    })
    @PreAuthorize("hasRole('ADMIN_ROLE')")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponseData<TransactionsResponse>> getTransactionById(@PathVariable Long id) {
        log.info("Fetching transaction with ID: {}", id);
        TransactionsResponse response = transactionService.getByID(id);
        return ResponseEntity.ok(ApiResponseData.success(response));
    }

    @Operation(
            summary = "Filter transactions by date range",
            description = "Retrieves transactions created between two specified dates (inclusive)."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Filtered transactions retrieved successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid date format or range")
    })
    @PreAuthorize("hasRole('ADMIN_ROLE')")
    @GetMapping("/between")
    public ResponseEntity<ApiResponseData<List<TransactionsResponse>>> getTransactionsBetweenDates(
            @Parameter(description = "Start date in dd-MM-yyyy format", example = "01-01-2024")
            @RequestParam("start") @DateTimeFormat(pattern = "dd-MM-yyyy") LocalDate start,

            @Parameter(description = "End date in dd-MM-yyyy format", example = "31-12-2024")
            @RequestParam("end") @DateTimeFormat(pattern = "dd-MM-yyyy") LocalDate end
    ) {
        log.info("Fetching transactions between: {} and {}", start, end);
        List<TransactionsResponse> events = transactionService.getByCreationDateBetween(start, end);
        return ResponseEntity.ok(ApiResponseData.success(events));
    }
}