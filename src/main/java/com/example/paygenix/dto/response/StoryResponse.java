package com.example.paygenix.dto.response;

import com.example.paygenix.enums.MediaType;

import java.time.Instant;

public record StoryResponse(Long storyId,
                            MediaType mediaType,
                            String mediaUrl,
                            Instant expiresAt,
                            boolean viewed) {
}
