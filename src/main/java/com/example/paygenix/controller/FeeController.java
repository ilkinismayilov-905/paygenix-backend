package com.example.paygenix.controller;

import com.example.paygenix.config.FeeProperties;
import com.example.paygenix.dto.ApiResponseData;
import com.example.paygenix.dto.response.FeesResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/fees")
@RequiredArgsConstructor
@Tag(name = "Fees", description = "Current fee rates")
@SecurityRequirement(name = "bearerAuth")
public class FeeController {

    private final FeeProperties fees;

    @Operation(summary = "Fee rates (T-129)", description = "commissionPercent is added to every transfer; cashbackPercent of "
            + "every phone payment comes back to the card. Amounts round half-even to 2 decimals.")
    @GetMapping
    public ResponseEntity<ApiResponseData<FeesResponse>> fees() {
        return ResponseEntity.ok(ApiResponseData.success(new FeesResponse(
                fees.commissionPercent().toPlainString(), fees.cashbackPercent().toPlainString())));
    }
}
