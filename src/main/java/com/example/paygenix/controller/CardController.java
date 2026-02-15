package com.example.paygenix.controller;

import com.example.paygenix.dto.ApiResponse;
import com.example.paygenix.dto.request.CardRequest;
import com.example.paygenix.dto.response.CardResponse;
import com.example.paygenix.service.CardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/cards")
@RequiredArgsConstructor
@Slf4j
public class CardController {

    private final CardService cardService;

    @PostMapping
    public ResponseEntity<ApiResponse<CardResponse>> addCard(@RequestBody CardRequest request) {
        CardResponse card = cardService.addCard(request);

        return ResponseEntity.ok(ApiResponse.build(
                HttpStatus.CREATED,
                "Card added successfully",
                card
        ));
    }

    // -------------------- GET CARD BY NUMBER --------------------
    @GetMapping("/number/{cardNumber}")
    public ResponseEntity<ApiResponse<CardResponse>> getCardByNumber(@PathVariable String cardNumber) {
        CardResponse card = cardService.getCardByNumber(cardNumber);
        return ResponseEntity.ok(ApiResponse.success(
                card
        ));
    }

    // -------------------- GET CARD BY ID --------------------
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CardResponse>> getCardById(@PathVariable Long id) {
        CardResponse card = cardService.findById(id);
        return ResponseEntity.ok(ApiResponse.success(
                card
        ));
    }

    // -------------------- GET ALL CARDS --------------------
    @GetMapping
    public ResponseEntity<ApiResponse<List<CardResponse>>>  getAllCards() {
        List<CardResponse> cards = cardService.getAllCards();
        return ResponseEntity.ok(ApiResponse.success(
                cards
        ));
    }

    // -------------------- UPDATE CARD --------------------
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CardResponse>>  updateCard(
            @PathVariable Long id,
            @RequestBody CardRequest request
    ) {
        CardResponse card = cardService.updateCard(id, request);
        return ResponseEntity.ok(ApiResponse.success(
                card
        ));
    }

    // -------------------- DELETE CARD --------------------
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<CardResponse>>  deleteCard(@PathVariable Long id) {
        CardResponse card = cardService.findById(id);
        cardService.deleteCard(id);

        return ResponseEntity.ok(ApiResponse.success(
                    card
        ));

    }

    // -------------------- ADD AMOUNT --------------------
    @PostMapping("/{cardNumber}/add-amount")
    public ResponseEntity<ApiResponse<CardResponse>>  addAmount(
            @PathVariable String cardNumber,
            @RequestParam BigDecimal amount
    ) {
        CardResponse card = cardService.addAmount(cardNumber, amount);
        return ResponseEntity.ok(ApiResponse.success(
                card
        ));

    }
}
