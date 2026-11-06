package com.example.paygenix.service;

import com.example.paygenix.entity.Story;
import com.example.paygenix.entity.User;
import com.example.paygenix.enums.MediaType;
import com.example.paygenix.enums.StoryVisibility;
import com.example.paygenix.media.StoryMediaStorage;
import com.example.paygenix.repository.StoryRepository;
import com.example.paygenix.repository.StoryViewRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StoryServiceTest {

    private final StoryRepository storyRepo = mock(StoryRepository.class);
    private final StoryViewRepository svRepo = mock(StoryViewRepository.class);
    private final MediaService mediaService = mock(MediaService.class);
    private final StoryMediaStorage storage = mock(StoryMediaStorage.class);
    private final StoryService service = new StoryService(storyRepo, svRepo, mediaService, storage);

    private static User admin() {
        User u = new User();
        u.setId(7L);
        u.setUsername("ilkin");
        return u;
    }

    // T-097: each story's own object is presigned, not a fixed placeholder image.
    @Test
    void feedPresignsEachStorysOwnObjectInOneBrandRow() {
        ReflectionTestUtils.setField(service, "appName", "Paygenix");
        Story story = Story.builder().id(1L).user(admin()).objectName("stories/7/a.jpg").mediaType(MediaType.IMAGE)
                .visibility(StoryVisibility.PUBLIC).createdAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        when(storyRepo.findLivePromos(any())).thenReturn(List.of(story));
        when(mediaService.getDownloadUrl("stories/7/a.jpg")).thenReturn("https://minio/signed-a");

        var feed = service.getFeed(admin());

        assertThat(feed.groups()).hasSize(1);
        assertThat(feed.groups().getFirst().username()).isEqualTo("Paygenix");
        assertThat(feed.groups().getFirst().userId()).as("which admin published it stays internal").isNull();
        assertThat(feed.groups().getFirst().stories().getFirst().mediaUrl()).isEqualTo("https://minio/signed-a");
    }

    // T-097: if the Story row cannot be saved, the uploaded object is removed instead of left orphaned.
    @Test
    void failedSaveRemovesUploadedObject() {
        var file = new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[]{1});
        when(storage.uploadStoryMedia(file, 7L)).thenReturn("stories/7/a.jpg");
        when(storyRepo.save(any())).thenThrow(new DataIntegrityViolationException("boom"));

        assertThatThrownBy(() -> service.publish(admin(), file, Duration.ofHours(24)))
                .isInstanceOf(DataIntegrityViolationException.class);
        verify(storage).deleteQuietly("stories/7/a.jpg");
    }

    // T-098: taking a story down removes its object.
    @Test
    void deleteRemovesGeneratedObjectButNotLegacySharedKeys() {
        Story fresh = Story.builder().id(1L).user(admin()).objectName("stories/7/a.jpg").visibility(StoryVisibility.PUBLIC).build();
        Story legacy = Story.builder().id(2L).user(admin()).objectName("centralBaku.jpeg").visibility(StoryVisibility.PUBLIC).build();
        when(storyRepo.findById(1L)).thenReturn(Optional.of(fresh));
        when(storyRepo.findById(2L)).thenReturn(Optional.of(legacy));

        service.delete(1L);
        service.delete(2L);

        assertThat(fresh.isDeleted()).isTrue();
        assertThat(legacy.isDeleted()).isTrue();
        verify(storage).deleteQuietly("stories/7/a.jpg");
        verify(storage, never()).deleteQuietly("centralBaku.jpeg");
    }
}
