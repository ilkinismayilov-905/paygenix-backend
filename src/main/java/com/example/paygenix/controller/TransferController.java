package com.example.paygenix.controller;

import com.example.paygenix.dto.ApiResponseData;
import com.example.paygenix.dto.request.TransferRequest;
import com.example.paygenix.dto.response.TransferResponse;
import com.example.paygenix.entity.User;
import com.example.paygenix.service.IdempotencyService;
import com.example.paygenix.service.TransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/transfer")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Transfer Management", description = "APIs for executing and viewing money transfers")
@SecurityRequirement(name = "bearerAuth")
public class TransferController {

    private final TransferService transferService;
    private final IdempotencyService idempotencyService;

    @Operation(
            summary = "Create a new transfer",
            description = "Moves money from one of the caller's cards to any card in the same currency, confirmed with the caller's PIN. A 2% commission is added to the debit."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Transfer completed successfully"),
            @ApiResponse(responseCode = "400", description = "Validation failed, same card, currency mismatch, or Idempotency-Key header blank or over 255 characters (INVALID_IDEMPOTENCY_KEY)"),
            @ApiResponse(responseCode = "401", description = "Unauthorized access"),
            @ApiResponse(responseCode = "403", description = "Wrong PIN (INVALID_PIN), or current Terms/Privacy not accepted (LEGAL_ACCEPTANCE_REQUIRED)"),
            @ApiResponse(responseCode = "404", description = "Card not found"),
            @ApiResponse(responseCode = "429", description = "Too many wrong PINs; PIN checks locked for 15 minutes (PIN_LOCKED); see Retry-After"),
            @ApiResponse(responseCode = "409", description = "Insufficient balance for amount plus commission, or card busy (retry with the same Idempotency-Key)"),
            @ApiResponse(responseCode = "422", description = "Idempotency-Key already used for a different request")
    })
    @PostMapping
    public ResponseEntity<ApiResponseData<TransferResponse>> createTransfer(
            @AuthenticationPrincipal User user,
            @Parameter(description = "Client-generated unique key (e.g. UUID); a retry with the same key returns the first result")
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody TransferRequest transferRequest
    ) {
        log.info("Initiating a new transfer request");
        TransferResponse transfer = idempotencyService.execute(user, idempotencyKey,
                "transfer|" + transferRequest.fromCardId() + "|" + transferRequest.toCardNumber()
                        + "|" + transferRequest.amount().stripTrailingZeros().toPlainString(),
                TransferResponse.class, () -> transferService.transfer(user, transferRequest));
        return ResponseEntity.ok(ApiResponseData.success(transfer));
    }

    @Operation(
            summary = "Get all transfers",
            description = "Retrieves all transfers of all users (admin only)."
    )
    @ApiResponse(responseCode = "200", description = "Transfer list retrieved successfully")
    @PreAuthorize("hasRole('ADMIN_ROLE')")
    @GetMapping
    public ResponseEntity<ApiResponseData<List<TransferResponse>>> getTransfers() {
        log.info("Fetching all transfers");
        List<TransferResponse> list = transferService.getAllTransfers();
        return ResponseEntity.ok(ApiResponseData.success(list));
    }

    @Operation(
            summary = "Get transfer by ID",
            description = "Retrieves specific details of a transfer using its unique internal ID."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Transfer details retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Transfer not found with the given ID")
    })
    @PreAuthorize("hasRole('ADMIN_ROLE')")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponseData<TransferResponse>> getTransferById(
            @Parameter(description = "Unique ID of the transfer", example = "105")
            @PathVariable Long id
    ) {
        log.info("Fetching transfer details for ID: {}", id);
        TransferResponse response = transferService.getTransferById(id);
        return ResponseEntity.ok(ApiResponseData.success(response));
    }
}