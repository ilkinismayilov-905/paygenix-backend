package com.example.paygenix.security;

import com.example.paygenix.entity.Story;
import com.example.paygenix.entity.User;
import com.example.paygenix.enums.MediaType;
import com.example.paygenix.enums.StoryVisibility;
import com.example.paygenix.repository.StoryRepository;
import com.example.paygenix.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// SEC-27: controllers asked for a Jwt principal, but JwtFilter sets a User, so the argument was null (NPE → 500).
class AuthenticationPrincipalTest extends IntegrationTest {

    @Autowired StoryRepository storyRepository;

    @Test
    void storyFeedResolvesTheLoggedInUser() throws Exception {
        mockMvc.perform(get("/api/v1/stories/feed").header("Authorization", bearer(createUser())))
                .andExpect(status().isOk());
    }

    @Test
    void anAdminCanTakeAPromoDown() throws Exception {
        User owner = createUser(com.example.paygenix.enums.Role.ADMIN_ROLE);
        Story story = storyOf(owner);

        mockMvc.perform(delete("/api/v1/admin/stories/" + story.getId()).header("Authorization", bearer(owner)))
                .andExpect(status().isOk());

        assertThat(storyRepository.findById(story.getId()).orElseThrow().isDeleted()).isTrue();
    }

    @Test
    void aUserCannotTakeAPromoDown() throws Exception {
        Story story = storyOf(createUser(com.example.paygenix.enums.Role.ADMIN_ROLE));

        int status = mockMvc.perform(delete("/api/v1/admin/stories/" + story.getId()).header("Authorization", bearer(createUser())))
                .andReturn().getResponse().getStatus();

        assertThat(status).isGreaterThanOrEqualTo(400);
        assertThat(storyRepository.findById(story.getId()).orElseThrow().isDeleted()).isFalse();
    }

    private Story storyOf(User owner) {
        Instant now = Instant.now();
        return storyRepository.save(Story.builder()
                .user(owner)
                .objectName("stories/" + owner.getId() + "/test.jpg")
                .mediaType(MediaType.IMAGE)
                .visibility(StoryVisibility.PUBLIC)
                .createdAt(now)
                .expiresAt(now.plus(1, ChronoUnit.DAYS))
                .deleted(false)
                .build());
    }
}
