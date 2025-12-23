package com.example.paygenix.repository;

import com.example.paygenix.entity.Story;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface StoryRepository extends JpaRepository<Story, Long> {

    @Query(
            """
select  s from Story s 
join fetch s.user u 
where u.id in :userIds
and s.expiresAt> :now
and s.deleted = false
"""
    )
    List<Story> findActiveStoriesWithUser(@Param("userIds") List<Long> userIds,
                                          @Param("now") Instant now);
}
