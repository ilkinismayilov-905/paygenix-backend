package com.example.paygenix.controller;

import com.example.paygenix.dto.ApiResponseData;
import com.example.paygenix.dto.request.CardRequest;
import com.example.paygenix.dto.response.CardResponse;
import com.example.paygenix.dto.response.IssuedCardResponse;
import com.example.paygenix.entity.User;
import com.example.paygenix.service.CardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cards")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Card Management",
        description = "Virtual cards: issue, list and close")
@SecurityRequirement(name = "bearerAuth")
public class CardController {

    private final CardService cardService;

    @Operation(summary = "Issue a new virtual card",
            description = "Issues a card in the chosen currency for the logged-in user. The full card number is in this response only; afterwards just the last four digits are shown.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Card issued"),
            @ApiResponse(responseCode = "400", description = "Invalid request payload"),
            @ApiResponse(responseCode = "401", description = "Unauthorized access")
    })
    @PostMapping
    public ResponseEntity<ApiResponseData<IssuedCardResponse>> issueCard(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody CardRequest request) {
        IssuedCardResponse card = cardService.issueCard(user, request);
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
