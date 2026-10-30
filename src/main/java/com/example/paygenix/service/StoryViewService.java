package com.example.paygenix.service;

import com.example.paygenix.entity.StoryView;
import com.example.paygenix.exceptions.NotFoundException;
import com.example.paygenix.repository.StoryRepository;
import com.example.paygenix.repository.StoryViewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;


@Service
@RequiredArgsConstructor
public class StoryViewService {

    private final StoryViewRepository storyViewRepo;
    private final StoryRepository storyRepo;

    @Transactional
    public void markViewed(Long storyId, Long viewerId) {
        if (!storyRepo.existsByIdAndDeletedFalseAndExpiresAtAfter(storyId, Instant.now())) {
            throw new NotFoundException("Story not found");
        }
        // Unique constraint var, təkrar insert olmamalıdır
        if (storyViewRepo.existsByStoryIdAndViewerId(storyId, viewerId)) return;

        storyViewRepo.save(StoryView.builder()
                .storyId(storyId)
                .viewerId(viewerId)
                .viewedAt(Instant.now())
                .build());
    }
}
