package com.example.paygenix.entity;

import com.example.paygenix.enums.ReportCategory;
import com.example.paygenix.enums.ReportStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "problem_reports", indexes = {
        @Index(name = "idx_problem_reports_user_id", columnList = "user_id"),
        @Index(name = "idx_problem_reports_status", columnList = "status"),
        @Index(name = "idx_problem_reports_created_at", columnList = "created_at")
})
@Data
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProblemReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 40)
    private ReportCategory category;

    @Column(name = "message", nullable = false, length = 2000)
    private String message;

    // optional: what the report is about (card ending, amount, date), as the user typed it
    @Column(name = "reference_id", length = 100)
    private String referenceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ReportStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public ProblemReport(Long userId, ReportCategory category, String message, String referenceId) {
        this.userId = userId;
        this.category = category;
        this.message = message;
        this.referenceId = referenceId;
        this.status = ReportStatus.OPEN;
    }


    @PrePersist
    public void prePersist() {
        status = ReportStatus.OPEN;
        createdAt = Instant.now();
    }


}

