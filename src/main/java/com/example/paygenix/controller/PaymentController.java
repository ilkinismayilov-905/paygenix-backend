package com.example.paygenix.controller;

import com.example.paygenix.dto.request.ConfirmPaymentRequest;
import com.example.paygenix.dto.request.PaymentRequest;
import com.example.paygenix.dto.request.TransferRequest;
import com.example.paygenix.dto.response.PaymentResponse;
import com.example.paygenix.dto.response.PaymentTransactionResponse;
import com.example.paygenix.dto.response.TransferResponse;
import com.example.paygenix.entity.PaymentTransaction;
import com.example.paygenix.entity.UserPrincipal;
import com.example.paygenix.service.PaymentService;
import com.example.paygenix.service.PaymentTransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;


@RestController
@RequestMapping("/api/payment")
@CrossOrigin
@RequiredArgsConstructor
@Slf4j
public class PaymentController {
    private final PaymentService paymentService;
    private final PaymentTransactionService ptService;

    @PostMapping("/initiate")
    public ResponseEntity<String> initiatePayment(
            @RequestBody PaymentRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        String username = jwt.getSubject();

        log.info("JWT subject = {}", jwt.getSubject());


        String flowId = paymentService.transfer(request, username);
        return ResponseEntity.ok(flowId);
    }

    @PostMapping("/confirm")
    public ResponseEntity<PaymentResponse> confirmPayment(
            @RequestBody ConfirmPaymentRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        String username = jwt.getSubject();

        PaymentResponse response =
                paymentService.confirmPayment(
                        request.flowId(),
                        request.pin(),
                        username
                );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/history")
    public ResponseEntity<List<PaymentTransactionResponse>> history(){
        List<PaymentTransactionResponse> list = ptService.getHistory();
        return ResponseEntity.ok(list);
    }

    private UserDetails extractUser(Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("Unauthenticated request");
        }

        Object principal = authentication.getPrincipal();

        if (!(principal instanceof UserDetails user)) {
            throw new RuntimeException("Invalid authentication principal");
        }

        return user;
    }
}
