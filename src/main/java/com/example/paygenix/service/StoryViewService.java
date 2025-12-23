package com.example.paygenix.service;

import com.example.paygenix.entity.StoryView;
import com.example.paygenix.repository.StoryViewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;


@Service
@RequiredArgsConstructor
public class StoryViewService {

    private final StoryViewRepository storyViewRepo;

    @Transactional
    public void markViewed(Long storyId, Long viewerId) {
        // Unique constraint var, təkrar insert olmamalıdır
        if (storyViewRepo.existsByStoryIdAndViewerId(storyId, viewerId)) return;

        storyViewRepo.save(StoryView.builder()
                .storyId(storyId)
                .viewerId(viewerId)
                .viewedAt(Instant.now())
                .build());
    }
}
