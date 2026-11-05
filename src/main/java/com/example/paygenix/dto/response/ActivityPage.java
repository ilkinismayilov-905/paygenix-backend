package com.example.paygenix.dto.response;

import java.util.List;

// Newest first; ask for page + 1 while hasNext is true.
public record ActivityPage(List<ActivityItem> items, int page, int size, boolean hasNext) {
}
