package com.example.paygenix.controller;

import com.example.paygenix.dto.ApiResponseData;
import org.springframework.security.access.prepost.PreAuthorize;
import com.example.paygenix.dto.request.ConfirmPaymentRequest;
import com.example.paygenix.dto.request.PaymentRequest;
import com.example.paygenix.dto.response.PaymentResponse;
import com.example.paygenix.dto.response.PaymentTransactionResponse;
import com.example.paygenix.service.PaymentService;
import com.example.paygenix.service.PaymentTransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
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
    private final PaymentTransactionService ptService;

    @Operation(
            summary = "Initiate payment transfer",
            description = "Starts a payment flow between cards. Returns a Flow ID required for confirmation."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Payment flow initiated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid payment request (e.g., insufficient balance)"),
            @ApiResponse(responseCode = "401", description = "Unauthorized access")
    })
    // shortcut: Jwt principal is always null here (SEC-27), so initiate/confirm return 500. Kept until card ownership
    // and payer checks (SEC-7) land; switching to the User principal first would make SEC-7 exploitable.
    @PostMapping("/initiate")
    public ResponseEntity<ApiResponseData<String>> initiatePayment(
            @Valid @RequestBody PaymentRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        String username = jwt.getSubject();
        log.info("Initiating payment for user: {}", username);

        String flowId = paymentService.transfer(request, username);
        return ResponseEntity.ok(ApiResponseData.success(flowId));
    }

    @Operation(
            summary = "Confirm payment",
            description = "Finalizes the payment using the Flow ID and user's secure PIN."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Payment confirmed and processed successfully"),
            @ApiResponse(responseCode = "403", description = "Incorrect PIN provided"),
            @ApiResponse(responseCode = "404", description = "Payment flow ID not found")
    })
    @PostMapping("/confirm")
    public ResponseEntity<ApiResponseData<PaymentResponse>> confirmPayment(
            @Valid @RequestBody ConfirmPaymentRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        String username = jwt.getSubject();
        log.info("Confirming payment flow: {} for user: {}", request.flowId(), username);

        PaymentResponse response = paymentService.confirmPayment(
                request.flowId(),
                request.pin(),
                username
        );

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