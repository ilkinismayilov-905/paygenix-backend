package com.example.paygenix.service;

import com.example.paygenix.dto.response.StoryFeedResponse;
import com.example.paygenix.dto.response.StoryGroupResponse;
import com.example.paygenix.dto.response.StoryResponse;
import com.example.paygenix.dto.response.StoryUploadResponse;
import com.example.paygenix.exceptions.NotFoundException;
import com.example.paygenix.entity.Story;
import com.example.paygenix.entity.StoryView;
import com.example.paygenix.entity.User;
import com.example.paygenix.enums.MediaType;
import com.example.paygenix.enums.StoryVisibility;
import com.example.paygenix.repository.StoryRepository;
import com.example.paygenix.repository.StoryViewRepository;
import com.example.paygenix.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class StoryService {

    private final StoryRepository storyRepo;
    private final StoryViewRepository svRepo;
    private final MediaService mediaService;
    private final UserRepository userRepo;

    @Value("${story.expiry-seconds}")
    private long expirySeconds;

    public List<Long> resolveFeedUserIds(Long currentUserId) {
        return List.of(currentUserId);
    }

    @Transactional
    public StoryUploadResponse uploadStory(
            User currentUser,
            MultipartFile file,
            MediaType mediaType,
            StoryVisibility visibility
    ) {
        String objectName = mediaService.upload(file);
        Instant now = Instant.now();

        Story saved = storyRepo.save(Story.builder()
                .user(currentUser)
                .objectName(objectName)
                .mediaType(mediaType)
                .visibility(visibility)
                .createdAt(now)
                .expiresAt(now.plusSeconds(expirySeconds))
                .deleted(false)
                .build());

        return new StoryUploadResponse(saved.getId(), saved.getObjectName());
    }

    // =========================
    // FEED (STORY PANEL)
    // =========================
    @Transactional(readOnly = true)
    public StoryFeedResponse getFeed(User currentUser) {
        Instant now = Instant.now();

        List<Long> feedUserIds = resolveFeedUserIds(currentUser.getId());
        if (feedUserIds.isEmpty()) {
            return new StoryFeedResponse(List.of());
        }

        // JOIN FETCH ilə User da eyni query-də gəlir (N+1 YOX)
        List<Story> stories =
                storyRepo.findActiveStoriesWithUser(feedUserIds, now);

        if (stories.isEmpty()) {
            return new StoryFeedResponse(List.of());
        }

        // Seen story-ləri tap
        List<Long> storyIds = stories.stream()
                .map(Story::getId)
                .toList();

        Set<Long> viewedStoryIds = new HashSet<>(
                svRepo.findViewedStoryIds(currentUser.getId(), storyIds)
        );

        // group by user
        Map<User, List<Story>> byUser = stories.stream()
                .collect(Collectors.groupingBy(
                        Story::getUser,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        List<StoryGroupResponse> groups = new ArrayList<>();

        for (Map.Entry<User, List<Story>> entry : byUser.entrySet()) {
            User user = entry.getKey();
            List<Story> userStories = entry.getValue();

            Instant latestStoryAt = userStories.stream()
                    .map(Story::getCreatedAt)
                    .max(Comparator.naturalOrder())
                    .orElse(now);

            List<StoryResponse> items = userStories.stream()
                    .map(s -> new StoryResponse(
                            s.getId(),
                            s.getMediaType(),
                            mediaService.getDownloadUrl("centralBaku.jpeg"),
                            s.getExpiresAt(),
                            viewedStoryIds.contains(s.getId())
                    ))
                    .toList();
            boolean hasUnseen = items.stream().anyMatch(i -> !i.viewed());

            groups.add(new StoryGroupResponse(
                    user.getId(),
                    user.getUsername(),   // JOIN FETCH sayəsində hazırdır
                    hasUnseen,
                    latestStoryAt,
                    items
            ));
        }

        // Sıralama: unseen olanlar yuxarı, sonra ən yeni story
        groups.sort(
                Comparator.comparing(StoryGroupResponse::hasUnseen).reversed()
                        .thenComparing(
                                StoryGroupResponse::latestStoryAt,
                                Comparator.reverseOrder()
                        )
        );

        return new StoryFeedResponse(groups);
    }

    // =========================
    // VIEW (SEEN)
    // =========================
    @Transactional
    public void markViewed(User currentUser, Long storyId) {
        if (svRepo.existsByStoryIdAndViewerId(storyId, currentUser.getId())) {
            return;
        }

        svRepo.save(StoryView.builder()
                .storyId(storyId)
                .viewerId(currentUser.getId())
                .viewedAt(Instant.now())
                .build());
    }

    // =========================
    // DELETE (ONLY OWNER)
    // =========================
    @Transactional
    public void deleteStory(User currentUser, Long storyId) {
        Story story = storyRepo.findById(storyId)
                .orElseThrow(() -> new NotFoundException("Story not found"));

        if (!story.getUser().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("Only the author can delete this story");
        }

        story.setDeleted(true);
        storyRepo.save(story);
    }

    @Transactional(readOnly = true)
    public String getMediaDownloadUrl(User currentUser, String objectName) {
        return mediaService.getDownloadUrl(objectName);
    }
}
