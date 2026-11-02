package com.example.paygenix.service;

import com.example.paygenix.entity.Story;
import com.example.paygenix.entity.User;
import com.example.paygenix.enums.MediaType;
import com.example.paygenix.enums.StoryVisibility;
import com.example.paygenix.media.StoryMediaStorage;
import com.example.paygenix.repository.StoryRepository;
import com.example.paygenix.repository.StoryViewRepository;
import com.example.paygenix.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StoryServiceTest {

    private final StoryRepository storyRepo = mock(StoryRepository.class);
    private final StoryViewRepository svRepo = mock(StoryViewRepository.class);
    private final MediaService mediaService = mock(MediaService.class);
    private final StoryMediaStorage storage = mock(StoryMediaStorage.class);
    private final StoryService service =
            new StoryService(storyRepo, svRepo, mediaService, storage, mock(UserRepository.class));

    private static User user() {
        User u = new User();
        u.setId(7L);
        u.setUsername("ilkin");
        return u;
    }

    // T-097: each story's own object is presigned, not a fixed placeholder image.
    @Test
    void feedPresignsEachStorysOwnObject() {
        User u = user();
        Story story = Story.builder().id(1L).user(u).objectName("stories/7/a.jpg").mediaType(MediaType.IMAGE)
                .createdAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        when(storyRepo.findActiveStoriesWithUser(anyList(), any())).thenReturn(List.of(story));
        when(mediaService.getDownloadUrl("stories/7/a.jpg")).thenReturn("https://minio/signed-a");

        var feed = service.getFeed(u);

        assertThat(feed.groups().getFirst().stories().getFirst().mediaUrl()).isEqualTo("https://minio/signed-a");
    }

    // T-097: if the Story row cannot be saved, the uploaded object is removed instead of left orphaned.
    @Test
    void failedSaveRemovesUploadedObject() {
        ReflectionTestUtils.setField(service, "expirySeconds", 60L);
        var file = new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[]{1});
        when(storage.uploadStoryMedia(file, 7L)).thenReturn("stories/7/a.jpg");
        when(storyRepo.save(any())).thenThrow(new DataIntegrityViolationException("boom"));

        assertThatThrownBy(() -> service.uploadStory(user(), file, MediaType.IMAGE, StoryVisibility.PUBLIC))
                .isInstanceOf(DataIntegrityViolationException.class);
        verify(storage).deleteQuietly("stories/7/a.jpg");
    }

    // T-098: deleting a story removes its object, so the owner can no longer presign it.
    @Test
    void deleteRemovesGeneratedObjectButNotLegacySharedKeys() {
        User u = user();
        Story fresh = Story.builder().id(1L).user(u).objectName("stories/7/a.jpg").build();
        Story legacy = Story.builder().id(2L).user(u).objectName("centralBaku.jpeg").build();
        when(storyRepo.findById(1L)).thenReturn(Optional.of(fresh));
        when(storyRepo.findById(2L)).thenReturn(Optional.of(legacy));

        service.deleteStory(u, 1L);
        service.deleteStory(u, 2L);

        assertThat(fresh.isDeleted()).isTrue();
        assertThat(legacy.isDeleted()).isTrue();
        verify(storage).deleteQuietly("stories/7/a.jpg");
        verify(storage, never()).deleteQuietly("centralBaku.jpeg");
    }
}
