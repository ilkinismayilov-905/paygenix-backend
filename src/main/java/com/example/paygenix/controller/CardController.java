package com.example.paygenix.controller;

import com.example.paygenix.dto.ApiResponseData;
import com.example.paygenix.dto.request.CardRequest;
import com.example.paygenix.dto.response.ActivityPage;
import com.example.paygenix.dto.response.CardResponse;
import com.example.paygenix.dto.response.IssuedCardResponse;
import com.example.paygenix.entity.User;
import com.example.paygenix.service.CardService;
import com.example.paygenix.service.IdempotencyService;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/cards")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Card Management",
        description = "Virtual cards: issue, list and close")
@SecurityRequirement(name = "bearerAuth")
public class CardController {

    private final CardService cardService;
    private final IdempotencyService idempotencyService;

    @Operation(summary = "Issue a new virtual card",
            description = "Issues a card in the chosen currency for the logged-in user. The full card number is in this response only; afterwards just the last four digits are shown. A retry with the same Idempotency-Key returns the same card without its number (cardNumber null).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Card issued"),
            @ApiResponse(responseCode = "400", description = "Invalid request payload, or Idempotency-Key header blank or over 255 characters (INVALID_IDEMPOTENCY_KEY)"),
            @ApiResponse(responseCode = "401", description = "Unauthorized access"),
            @ApiResponse(responseCode = "422", description = "Idempotency-Key already used for a different request")
    })
    @PostMapping
    public ResponseEntity<ApiResponseData<IssuedCardResponse>> issueCard(
            @AuthenticationPrincipal User user,
            @Parameter(description = "Client-generated unique key (e.g. UUID); a retry with the same key returns the first card")
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CardRequest request) {
        IssuedCardResponse card = idempotencyService.execute(user, idempotencyKey, "card|" + request.currency(),
                IssuedCardResponse.class, () -> cardService.issueCard(user, request),
                issued -> new IssuedCardResponse(null, issued.card())); // the full number is never stored
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponseData.build(
                HttpStatus.CREATED,
                "Card issued successfully",
                card
        ));
    }

    @Operation(summary = "List my cards", description = "Returns the logged-in user's cards.")
    @ApiResponse(responseCode = "200", description = "Cards retrieved")
    @GetMapping("/me")
    public ResponseEntity<ApiResponseData<List<CardResponse>>> getMyCards(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponseData.success(cardService.getMyCards(user)));
    }

    @Operation(summary = "My activity", description = "Money movements on the logged-in user's cards (closed cards included), "
            + "newest first. Optional cardId narrows it to one card.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Activity page"),
            @ApiResponse(responseCode = "400", description = "page < 0 or size outside 1..100")
    })
    @GetMapping("/me/activity")
    public ResponseEntity<ApiResponseData<ActivityPage>> getMyActivity(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) Long cardId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(ApiResponseData.success(cardService.getMyActivity(user, cardId, page, size)));
    }

    @Operation(summary = "Get one of my cards", description = "Returns a card owned by the logged-in user.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Card details retrieved"),
            @ApiResponse(responseCode = "404", description = "No such card for this user")
    })
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponseData<CardResponse>> getCardById(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponseData.success(cardService.getMyCard(user, id)));
    }

    @Operation(summary = "Get all cards", description = "Returns every card in the system (admin only).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "List of cards retrieved successfully")
    })
    @PreAuthorize("hasRole('ADMIN_ROLE')")
    @GetMapping
    public ResponseEntity<ApiResponseData<List<CardResponse>>> getAllCards() {
        return ResponseEntity.ok(ApiResponseData.success(cardService.getAllCards()));
    }

    @Operation(summary = "Close a card", description = "Closes one of the logged-in user's cards. The balance must be zero.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Card closed"),
            @ApiResponse(responseCode = "404", description = "No such card for this user"),
            @ApiResponse(responseCode = "409", description = "Card still holds a balance")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponseData<Void>> closeCard(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {
        cardService.closeCard(user, id);
        return ResponseEntity.ok(ApiResponseData.success(null));
    }
}
