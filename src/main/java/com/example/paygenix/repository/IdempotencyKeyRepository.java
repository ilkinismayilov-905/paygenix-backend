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

    @Modifying
    @Query("DELETE FROM IdempotencyKey k WHERE k.createdAt < :before")
    int deleteCreatedBefore(@Param("before") java.time.Instant before);
}
