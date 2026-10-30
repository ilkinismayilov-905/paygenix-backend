package com.example.paygenix.controller;

import com.example.paygenix.dto.ApiResponseData;
import com.example.paygenix.dto.request.ConfirmPaymentRequest;
import com.example.paygenix.dto.request.PaymentRequest;
import com.example.paygenix.dto.response.PaymentResponse;
import com.example.paygenix.dto.response.PaymentTransactionResponse;
import com.example.paygenix.entity.User;
import com.example.paygenix.service.IdempotencyService;
import com.example.paygenix.service.PaymentService;
import com.example.paygenix.service.PaymentTransactionService;
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
@RequestMapping("/api/payment")
@CrossOrigin
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Payment Management", description = "APIs for initiating, confirming, and viewing payment transactions")
@SecurityRequirement(name = "bearerAuth")
public class PaymentController {

    private final PaymentService paymentService;
    private final IdempotencyService idempotencyService;
    private final PaymentTransactionService ptService;

    @Operation(
            summary = "Initiate payment transfer",
            description = "Starts a payment flow between cards. Returns a Flow ID required for confirmation."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Payment flow initiated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid payment request (non-positive amount, malformed card number)"),
            @ApiResponse(responseCode = "404", description = "Card not found or not owned by the caller"),
            @ApiResponse(responseCode = "409", description = "Insufficient balance, or card busy (retry with the same Idempotency-Key)"),
            @ApiResponse(responseCode = "422", description = "Idempotency-Key already used for a different request"),
            @ApiResponse(responseCode = "401", description = "Unauthorized access")
    })
    @PostMapping("/initiate")
    public ResponseEntity<ApiResponseData<String>> initiatePayment(
            @Valid @RequestBody PaymentRequest request,
            @AuthenticationPrincipal User user,
            @Parameter(description = "Client-generated unique key (e.g. UUID); a retry with the same key returns the first result")
            @RequestHeader("Idempotency-Key") String idempotencyKey
    ) {
        log.info("Initiating payment for user: {}", user.getId());

        String flowId = idempotencyService.execute(user, idempotencyKey,
                "payment-initiate|" + request.phoneNumber() + "|" + request.fromCardNumber()
                        + "|" + request.amount().stripTrailingZeros().toPlainString(),
                String.class, () -> paymentService.transfer(request, user));
        return ResponseEntity.ok(ApiResponseData.success(flowId));
    }

    @Operation(
            summary = "Confirm payment",
            description = "Finalizes the payment using the Flow ID and user's secure PIN."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Payment confirmed and processed successfully"),
            @ApiResponse(responseCode = "400", description = "Validation failed (flow ID missing, PIN not 6 digits)"),
            @ApiResponse(responseCode = "404", description = "Payment flow not found, not owned by the caller, or invalid PIN"),
            @ApiResponse(responseCode = "409", description = "Payment already processed, insufficient balance, or busy (retry with the same Idempotency-Key)"),
            @ApiResponse(responseCode = "422", description = "Idempotency-Key already used for a different request")
    })
    @PostMapping("/confirm")
    public ResponseEntity<ApiResponseData<PaymentResponse>> confirmPayment(
            @Valid @RequestBody ConfirmPaymentRequest request,
            @AuthenticationPrincipal User user,
            @Parameter(description = "Client-generated unique key (e.g. UUID); a retry with the same key returns the first result")
            @RequestHeader("Idempotency-Key") String idempotencyKey
    ) {
        log.info("Confirming payment flow: {} for user: {}", request.flowId(), user.getId());

        PaymentResponse response = idempotencyService.execute(user, idempotencyKey,
                "payment-confirm|" + request.flowId(),
                PaymentResponse.class, () -> paymentService.confirmPayment(request.flowId(), request.pin(), user));

        return ResponseEntity.ok(ApiResponseData.success(response));
    }

    @Operation(
            summary = "Get transaction history",
            description = "Retrieves a list of all past payment transactions for the authenticated user."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Transaction history retrieved successfully")
    })
    @PreAuthorize("hasRole('ADMIN_ROLE')")
    @GetMapping("/history")
    public ResponseEntity<ApiResponseData<List<PaymentTransactionResponse>>> getPaymentHistory() {
        log.info("Fetching payment transaction history");
        List<PaymentTransactionResponse> list = ptService.getHistory();
        return ResponseEntity.ok(ApiResponseData.success(list));
    }
}