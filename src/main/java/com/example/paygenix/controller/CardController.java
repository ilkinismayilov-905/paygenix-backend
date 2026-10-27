package com.example.paygenix.controller;

import com.example.paygenix.dto.ApiResponseData;
import org.springframework.security.access.prepost.PreAuthorize;
import com.example.paygenix.dto.request.CardRequest;
import com.example.paygenix.dto.response.CardResponse;
import com.example.paygenix.service.CardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/cards")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Card Management",
        description = "APIs for managing bank cards, balance top-ups, and CRUD operations")
@SecurityRequirement(name = "bearerAuth")
public class CardController {

    private final CardService cardService;

    @Operation(summary = "Create a new card"
            , description = "Registers a new bank card in the system.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Card successfully created"),
            @ApiResponse(responseCode = "400", description = "Invalid request payload"),
            @ApiResponse(responseCode = "401", description = "Unauthorized access")
    })
    @PostMapping
    public ResponseEntity<ApiResponseData<CardResponse>> addCard(@Valid @RequestBody CardRequest request) {
        log.info("Request received to add a new card");
        CardResponse card = cardService.addCard(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponseData.build(
                HttpStatus.CREATED,
                "Card added successfully",
                card
        ));
    }

    @Operation(summary = "Get card by card number",
            description = "Retrieves card details based on the unique 16-digit card number.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Card found and retrieved"),
            @ApiResponse(responseCode = "404", description = "Card not found with the provided number")
    })
    @GetMapping("/number/{cardNumber}")
    public ResponseEntity<ApiResponseData<CardResponse>> getCardByNumber(
            @Parameter(description = "16-digit card number", example = "4169XXXXXXXX1234")
            @PathVariable String cardNumber) {
        log.info("Fetching card with number: {}", cardNumber);
        CardResponse card = cardService.getCardByNumber(cardNumber);
        return ResponseEntity.ok(ApiResponseData.success(card));
    }

    @Operation(summary = "Get card by ID", description = "Retrieves specific card information using its internal database ID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Card details retrieved"),
            @ApiResponse(responseCode = "404", description = "No card found with the given ID")
    })
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponseData<CardResponse>> getCardById(@PathVariable Long id) {
        log.info("Fetching card with ID: {}", id);
        CardResponse card = cardService.findById(id);
        return ResponseEntity.ok(ApiResponseData.success(card));
    }

    @Operation(summary = "Get all cards", description = "Returns a complete list of all cards registered in the system.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "List of cards retrieved successfully")
    })
    @PreAuthorize("hasRole('ADMIN_ROLE')")
    @GetMapping
    public ResponseEntity<ApiResponseData<List<CardResponse>>> getAllCards() {
        log.info("Fetching all registered cards");
        List<CardResponse> cards = cardService.getAllCards();
        return ResponseEntity.ok(ApiResponseData.success(cards));
    }

    @Operation(summary = "Update card details", description = "Updates an existing card's information by its ID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Card updated successfully"),
            @ApiResponse(responseCode = "400", description = "Validation failed for updated data"),
            @ApiResponse(responseCode = "404", description = "Card to update not found")
    })
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponseData<CardResponse>> updateCard(
            @PathVariable Long id,
            @Valid @RequestBody CardRequest request
    ) {
        log.info("Updating card details for ID: {}", id);
        CardResponse card = cardService.updateCard(id, request);
        return ResponseEntity.ok(ApiResponseData.success(card));
    }

    @Operation(summary = "Delete a card", description = "Permanently removes a card from the system using its ID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Card successfully deleted"),
            @ApiResponse(responseCode = "404", description = "Card to delete not found")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponseData<CardResponse>> deleteCard(@PathVariable Long id) {
        log.warn("Request to delete card with ID: {}", id);
        CardResponse card = cardService.findById(id);
        cardService.deleteCard(id);
        return ResponseEntity.ok(ApiResponseData.success(card));
    }

    @Operation(summary = "Add balance to card", description = "Increases the current balance of a specific card.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Amount successfully added to balance"),
            @ApiResponse(responseCode = "400", description = "Invalid amount provided"),
            @ApiResponse(responseCode = "404", description = "Target card not found")
    })
    @PostMapping("/{cardNumber}/add-amount")
    public ResponseEntity<ApiResponseData<CardResponse>> addAmount(
            @PathVariable String cardNumber,
            @Parameter(description = "Amount to be added", example = "100.00")
            @RequestParam BigDecimal amount
    ) {
        log.info("Adding amount {} to card number: {}", amount, cardNumber);
        CardResponse card = cardService.addAmount(cardNumber, amount);
        return ResponseEntity.ok(ApiResponseData.success(card));
    }
}