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

    // -------------------- ADD CARD --------------------
    @PostMapping
    public ResponseEntity<ApiResponse<CardResponse>> addCard(@RequestBody CardRequest request) {
        CardResponse card = cardService.addCard(request);
        ApiResponse<CardResponse> response = ApiResponse.build(
                HttpStatus.CREATED,
                "Card added successfully",
                card
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // -------------------- GET CARD BY NUMBER --------------------
    @GetMapping("/number/{cardNumber}")
    public ResponseEntity<Map<String, Object>> getCardByNumber(@PathVariable String cardNumber) {
        Map<String, Object> response = new HashMap<>();
        try {
            CardResponse card = cardService.getCardByNumber(cardNumber);
            response.put("success", true);
            response.put("data", card);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }
    }

    // -------------------- GET CARD BY ID --------------------
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getCardById(@PathVariable Long id) {
        Map<String, Object> response = new HashMap<>();
        try {
            CardResponse card = cardService.findById(id);
            response.put("success", true);
            response.put("data", card);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }
    }

    // -------------------- GET ALL CARDS --------------------
    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllCards() {
        Map<String, Object> response = new HashMap<>();
        try {
            List<CardResponse> cards = cardService.getAllCards();
            response.put("success", true);
            response.put("data", cards);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Failed to fetch cards");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // -------------------- UPDATE CARD --------------------
    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateCard(
            @PathVariable Long id,
            @RequestBody CardRequest request
    ) {
        Map<String, Object> response = new HashMap<>();
        try {
            CardResponse card = cardService.updateCard(id, request);
            response.put("success", true);
            response.put("data", card);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    // -------------------- DELETE CARD --------------------
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteCard(@PathVariable Long id) {
        Map<String, Object> response = new HashMap<>();
        try {
            cardService.deleteCard(id);
            response.put("success", true);
            response.put("message", "Card deleted successfully");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }
    }

    // -------------------- ADD AMOUNT --------------------
    @PostMapping("/{cardNumber}/add-amount")
    public ResponseEntity<Map<String, Object>> addAmount(
            @PathVariable String cardNumber,
            @RequestParam BigDecimal amount
    ) {
        Map<String, Object> response = new HashMap<>();
        try {
            CardResponse card = cardService.addAmount(cardNumber, amount);
            response.put("success", true);
            response.put("data", card);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Failed to add amount");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}
