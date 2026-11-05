package com.example.paygenix.repository;

import com.example.paygenix.entity.LedgerEntry;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;

@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM LedgerEntry e WHERE e.accountId = :accountId")
    BigDecimal balanceOf(Long accountId);

    // Native SQL on purpose: closed cards are hidden from JPQL (@SQLRestriction) but their history stays the owner's.
    @Query(value = """
            SELECT e.id AS "id", c.id AS "cardId", c.last4 AS "last4", e.kind AS "kind", e.amount AS "amount",
                   e.currency AS "currency", e.created_at AS "createdAt"
            FROM ledger_entries e
            JOIN ledger_accounts a ON a.id = e.account_id
            JOIN cards c ON c.id = a.card_id
            WHERE c.owner_id = :ownerId AND (CAST(:cardId AS bigint) IS NULL OR c.id = :cardId)
            ORDER BY e.created_at DESC, e.id DESC""", nativeQuery = true)
    Slice<ActivityRow> findActivity(Long ownerId, Long cardId, Pageable pageable);

    interface ActivityRow {
        Long getId();
        Long getCardId();
        String getLast4();
        String getKind();
        BigDecimal getAmount();
        String getCurrency();
        Instant getCreatedAt();
    }
}
