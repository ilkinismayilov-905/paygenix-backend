package com.example.paygenix.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** Append-only (DB trigger): rows are written by {@code UserLegalAcceptanceRepository.acceptOnce} only. */
@Getter
@NoArgsConstructor
@Entity
@Table(name = "user_legal_acceptances")
public class UserLegalAcceptance {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "document_id", nullable = false, updatable = false)
    private Long documentId;

    @Column(name = "accepted_at", nullable = false, insertable = false, updatable = false)
    private Instant acceptedAt;
}
