package com.example.paygenix.dto.response;

import java.util.List;

// Oldest first for PENDING (first come, first served), newest first otherwise; ask for page + 1 while hasNext.
public record KycReviewPage(List<KycReviewItem> items, int page, int size, boolean hasNext) {
}
