package com.example.paygenix.entity;

import com.example.paygenix.enums.MediaType;
import com.example.paygenix.enums.StoryVisibility;
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
@Table(name = "stories", indexes = {
        @Index(name = "idx_stories_user_expires", columnList = "userId,expiresAt"),
        @Index(name = "idx_stories_expires", columnList = "expiresAt")
})
@Data
public class Story {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // MinIO object key (filename)
    @Column(nullable = false, length = 512)
    private String objectName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MediaType mediaType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private StoryVisibility visibility;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean deleted;
}
