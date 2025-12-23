package com.example.paygenix.dto.response;

import java.time.Instant;
import java.util.List;

public record StoryGroupResponse(Long userId,
                                 String username,          // hələlik placeholder, user servisdən doldur
                                 boolean hasUnseen,
                                 Instant latestStoryAt,
                                 List<StoryResponse> stories) {
}
