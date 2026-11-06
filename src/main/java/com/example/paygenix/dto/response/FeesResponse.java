package com.example.paygenix.dto.response;

// Percent as decimal strings ("2", "0.5"), like money amounts: no float rounding on the way.
public record FeesResponse(String commissionPercent, String cashbackPercent) {
}
