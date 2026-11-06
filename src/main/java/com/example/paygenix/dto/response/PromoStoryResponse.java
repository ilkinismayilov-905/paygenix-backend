package com.example.paygenix.dto.response;

import com.example.paygenix.enums.MediaType;

import java.time.Instant;

// mediaUrl is presigned for story.presign-seconds; views counts distinct users.
public record PromoStoryResponse(Long id, MediaType mediaType, String mediaUrl, Instant createdAt, Instant expiresAt, long views) {
}
