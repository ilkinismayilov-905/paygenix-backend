package com.example.paygenix.entity;

import com.example.paygenix.enums.Currency;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

// Append-only (a DB trigger rejects UPDATE and DELETE).
@Entity
@Table(name = "ledger_entries")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tx_id", nullable = false)
    private UUID txId;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Currency currency;

    @Column(nullable = false, length = 32)
    private String kind;

    @Column(nullable = false, length = 64)
    private String reference;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;
}
