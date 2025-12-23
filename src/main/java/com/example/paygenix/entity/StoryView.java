package com.example.paygenix.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "story_views",
        uniqueConstraints = @UniqueConstraint(name = "uq_story_view", columnNames = {"storyId","viewerId"}),
        indexes = {
                @Index(name = "idx_story_views_story", columnList = "storyId"),
                @Index(name = "idx_story_views_viewer", columnList = "viewerId")
        })
@Data
public class StoryView {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long storyId;

    @Column(nullable = false)
    private Long viewerId;

    @Column(nullable = false)
    private Instant viewedAt;
}
