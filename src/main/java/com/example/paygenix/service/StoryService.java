package com.example.paygenix.service;

import com.example.paygenix.dto.response.PromoStoryResponse;
import com.example.paygenix.dto.response.StoryFeedResponse;
import com.example.paygenix.dto.response.StoryGroupResponse;
import com.example.paygenix.dto.response.StoryResponse;
import com.example.paygenix.entity.Story;
import com.example.paygenix.entity.User;
import com.example.paygenix.enums.MediaType;
import com.example.paygenix.enums.StoryVisibility;
import com.example.paygenix.exceptions.ApiException;
import com.example.paygenix.exceptions.NotFoundException;
import com.example.paygenix.media.StoryMediaStorage;
import com.example.paygenix.repository.StoryRepository;
import com.example.paygenix.repository.StoryViewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Promo stories: admins publish an image or a short video for a while (1 hour to 7 days); every signed-in user sees
 * them in one "Paygenix" row on the dashboard, oldest first, and which ones they already viewed. There is no follow
 * graph (out of scope), so users do not publish stories themselves.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StoryService {

    static final Duration MIN_DURATION = Duration.ofHours(1);
    static final Duration MAX_DURATION = Duration.ofDays(7);

    private final StoryRepository storyRepo;
    private final StoryViewRepository svRepo;
    private final MediaService mediaService;
    private final StoryMediaStorage storyMediaStorage;

    @Value("${app.name}")
    private String appName;

    // Not @Transactional: the upload must not run inside a DB transaction; if the save fails the object is removed.
    public PromoStoryResponse publish(User admin, MultipartFile file, Duration duration) {
        if (file == null || file.isEmpty()) {
            throw ApiException.validation("file: is required");
        }
        if (duration.compareTo(MIN_DURATION) < 0 || duration.compareTo(MAX_DURATION) > 0) {
            throw ApiException.validation("hours: must be between 1 and 168");
        }
        // the kind follows the stored file, never a separate client claim (StoryMediaStorage refuses other types)
        String type = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        MediaType mediaType = type.startsWith("video/") ? MediaType.VIDEO : MediaType.IMAGE;
        String objectName = storyMediaStorage.uploadStoryMedia(file, admin.getId());
        Instant now = Instant.now();
        Story saved;
        try {
            saved = storyRepo.save(Story.builder()
                    .user(admin)
                    .objectName(objectName)
                    .mediaType(mediaType)
                    .visibility(StoryVisibility.PUBLIC)
                    .createdAt(now)
                    .expiresAt(now.plus(duration))
                    .deleted(false)
                    .build());
        } catch (RuntimeException e) {
            storyMediaStorage.deleteQuietly(objectName);
            throw e;
        }
        log.info("Promo story {} published by user {}", saved.getId(), admin.getId());
        return new PromoStoryResponse(saved.getId(), saved.getMediaType(), mediaService.getDownloadUrl(objectName),
                saved.getCreatedAt(), saved.getExpiresAt(), 0);
    }

    /** Live promos for the admin screen, newest first, with how many users viewed each. */
    @Transactional(readOnly = true)
    public List<PromoStoryResponse> livePromos() {
        List<Story> stories = storyRepo.findLivePromos(Instant.now());
        Map<Long, Long> views = stories.isEmpty() ? Map.of()
                : svRepo.countViews(stories.stream().map(Story::getId).toList()).stream()
                        .collect(Collectors.toMap(r -> (Long) r[0], r -> (Long) r[1]));
        return stories.reversed().stream()
                .map(s -> new PromoStoryResponse(s.getId(), s.getMediaType(), mediaService.getDownloadUrl(s.getObjectName()),
                        s.getCreatedAt(), s.getExpiresAt(), views.getOrDefault(s.getId(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    public StoryFeedResponse getFeed(User viewer) {
        List<Story> stories = storyRepo.findLivePromos(Instant.now());
        if (stories.isEmpty()) {
            return new StoryFeedResponse(List.of());
        }
        Set<Long> viewed = new HashSet<>(svRepo.findViewedStoryIds(viewer.getId(), stories.stream().map(Story::getId).toList()));
        List<StoryResponse> items = stories.stream()
                .map(s -> new StoryResponse(s.getId(), s.getMediaType(), mediaService.getDownloadUrl(s.getObjectName()),
                        s.getExpiresAt(), viewed.contains(s.getId())))
                .toList();
        // one brand row: which admin published a promo is internal
        return new StoryFeedResponse(List.of(new StoryGroupResponse(null, appName,
                items.stream().anyMatch(i -> !i.viewed()), stories.getLast().getCreatedAt(), items)));
    }

    // Not @Transactional: the row is marked deleted first, then the object is removed outside any DB transaction.
    // Any admin may take any promo down: promos belong to the team, not to whoever published them.
    public void delete(Long storyId) {
        Story story = storyRepo.findById(storyId).filter(s -> !s.isDeleted() && s.getVisibility() == StoryVisibility.PUBLIC)
                .orElseThrow(() -> new NotFoundException("Story not found"));
        story.setDeleted(true);
        storyRepo.save(story);
        // Keys before T-095 were raw file names that other stories may share, so only generated keys are removed.
        if (story.getObjectName().startsWith("stories/" + story.getUser().getId() + "/")) {
            storyMediaStorage.deleteQuietly(story.getObjectName());
        }
        log.info("Promo story {} taken down", storyId);
    }
}
