package com.example.paygenix.topup;

import com.example.paygenix.dto.ApiResponseData;
import com.example.paygenix.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/topups")
@RequiredArgsConstructor
@Tag(name = "Top-ups", description = "Add money to a card through Stripe Checkout (test mode)")
@SecurityRequirement(name = "bearerAuth")
public class TopUpController {

    private final TopUpService topUpService;

    @Operation(summary = "Start a top-up", description = "Opens a Stripe Checkout for 1.00-10000.00 in the card's currency. Send the "
            + "browser to checkoutUrl; Stripe brings it back to FRONTEND_URL/top-up/return?session=... The card is credited only "
            + "when Stripe confirms the payment (webhook). A retry with the same Idempotency-Key returns the same checkout.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Checkout opened; status PENDING"),
            @ApiResponse(responseCode = "400", description = "Amount out of range (VALIDATION_FAILED) or bad Idempotency-Key (INVALID_IDEMPOTENCY_KEY)"),
            @ApiResponse(responseCode = "404", description = "Not one of the caller's open cards (NOT_FOUND)"),
            @ApiResponse(responseCode = "409", description = "The key's top-up is already paid or expired (TOP_UP_FINISHED); use a new key"),
            @ApiResponse(responseCode = "422", description = "Idempotency-Key used for a different top-up (IDEMPOTENCY_KEY_REUSED)"),
            @ApiResponse(responseCode = "503", description = "Stripe not configured or unreachable (TOP_UP_UNAVAILABLE), with Retry-After")
    })
    @PostMapping
    public ResponseEntity<ApiResponseData<TopUpResponse>> start(
            @AuthenticationPrincipal User user,
            @Parameter(description = "Client-generated unique key (e.g. UUID)") @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody TopUpRequest request) {
        TopUpService.Started started = topUpService.start(user, request.cardId(), request.amount(), idempotencyKey);
        TopUp t = topUpService.byId(user, started.id());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponseData.build(HttpStatus.CREATED, "Checkout opened", TopUpResponse.of(t)));
    }

    @Operation(summary = "A top-up by its checkout session", description = "For the return page: PENDING until Stripe's webhook "
            + "arrives (poll a few times), then SUCCEEDED, EXPIRED or REFUND_NEEDED.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "The top-up"),
            @ApiResponse(responseCode = "404", description = "Not the caller's top-up (NOT_FOUND)")
    })
    @GetMapping("/session/{sessionId}")
    public ResponseEntity<ApiResponseData<TopUpResponse>> bySession(@AuthenticationPrincipal User user, @PathVariable String sessionId) {
        return ResponseEntity.ok(ApiResponseData.success(TopUpResponse.of(topUpService.bySession(user, sessionId))));
    }
}
