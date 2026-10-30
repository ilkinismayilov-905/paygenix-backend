package com.example.paygenix.security;

import com.example.paygenix.entity.Story;
import com.example.paygenix.entity.User;
import com.example.paygenix.enums.MediaType;
import com.example.paygenix.enums.StoryVisibility;
import com.example.paygenix.repository.StoryRepository;
import com.example.paygenix.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Story views must point at a live story (V5 foreign key); a bad id is a 404, not a 500.
class StoryViewTest extends IntegrationTest {

    @Autowired StoryRepository storyRepository;

    @Test
    void viewingALiveStoryIsRecorded() throws Exception {
        User viewer = createUser();
        view(viewer, story(createUser(), Instant.now().plus(Duration.ofHours(1)), false)).andExpect(status().isOk());
        view(viewer, story(createUser(), Instant.now().plus(Duration.ofHours(1)), false)).andExpect(status().isOk());
    }

    @Test
    void unknownExpiredOrDeletedStoryIsNotFound() throws Exception {
        User viewer = createUser();
        User author = createUser();
        view(viewer, Long.MAX_VALUE).andExpect(status().isNotFound());
        view(viewer, story(author, Instant.now().minus(Duration.ofHours(1)), false)).andExpect(status().isNotFound());
        view(viewer, story(author, Instant.now().plus(Duration.ofHours(1)), true)).andExpect(status().isNotFound());
    }

    private Long story(User author, Instant expiresAt, boolean deleted) {
        return storyRepository.save(Story.builder()
                .user(author)
                .objectName("test/" + System.nanoTime())
                .mediaType(MediaType.IMAGE)
                .visibility(StoryVisibility.PUBLIC)
                .createdAt(Instant.now())
                .expiresAt(expiresAt)
                .deleted(deleted)
                .build()).getId();
    }

    private ResultActions view(User viewer, Long storyId) throws Exception {
        return mockMvc.perform(post("/api/stories/" + storyId + "/view").header("Authorization", bearer(viewer)));
    }
}
