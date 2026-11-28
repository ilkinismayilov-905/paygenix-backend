package com.example.paygenix.repository;

import com.example.paygenix.entity.IdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, Long> {

    // Claims the key. A concurrent insert of the same key waits on the unique index until the
    // other transaction ends, then inserts nothing (0) if it committed or claims the key (1) if it rolled back.
    @Modifying
    @Query(value = """
            INSERT INTO idempotency_keys (user_id, idem_key, request_hash, response_body)
            VALUES (:userId, :key, :requestHash, '')
            ON CONFLICT (user_id, idem_key) DO NOTHING""", nativeQuery = true)
    int claim(Long userId, String key, String requestHash);

    @Modifying
    @Query("UPDATE IdempotencyKey k SET k.responseBody = :body WHERE k.userId = :userId AND k.idemKey = :key")
    void saveResponse(Long userId, String key, String body);

    Optional<IdempotencyKey> findByUserIdAndIdemKey(Long userId, String idemKey);

    // executeInSteps: an action that failed gives its claim back (only a claim still without a response)
    @Modifying
    @Query(value = "DELETE FROM idempotency_keys WHERE user_id = :userId AND idem_key = :key AND response_body = ''",
            nativeQuery = true)
    int release(Long userId, String key);

    // executeInSteps: takes over an abandoned claim; the created_at match makes only one taker win
    @Modifying
    @Query(value = "UPDATE idempotency_keys SET created_at = now() "
            + "WHERE user_id = :userId AND idem_key = :key AND response_body = '' AND created_at = :seen", nativeQuery = true)
    int takeOver(Long userId, String key, java.time.Instant seen);

    @Modifying
    @Query("DELETE FROM IdempotencyKey k WHERE k.createdAt < :before")
    int deleteCreatedBefore(@Param("before") java.time.Instant before);
}
