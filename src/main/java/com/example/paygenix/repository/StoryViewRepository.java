package com.example.paygenix.repository;

import com.example.paygenix.entity.StoryView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface StoryViewRepository extends JpaRepository<StoryView, Long> {

    boolean existsByStoryIdAndViewerId(Long storyId, Long viewerId);

    @Query("""
           select sv.storyId from StoryView sv
           where sv.viewerId = :viewerId and sv.storyId in :storyIds
           """)
    List<Long> findViewedStoryIds(@Param("viewerId") Long viewerId,
                                  @Param("storyIds") Collection<Long> storyIds);
}
