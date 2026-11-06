package com.example.paygenix.repository;

import com.example.paygenix.entity.Story;
import com.example.paygenix.enums.StoryVisibility;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface StoryRepository extends JpaRepository<Story, Long> {

    boolean existsByIdAndDeletedFalseAndVisibilityAndExpiresAtAfter(Long id, StoryVisibility visibility, Instant now);

    // live promo stories, oldest first (the order they play in)
    @Query("""
            select s from Story s
            where s.visibility = com.example.paygenix.enums.StoryVisibility.PUBLIC
            and s.deleted = false and s.expiresAt > :now
            order by s.createdAt, s.id
            """)
    List<Story> findLivePromos(@Param("now") Instant now);

}
