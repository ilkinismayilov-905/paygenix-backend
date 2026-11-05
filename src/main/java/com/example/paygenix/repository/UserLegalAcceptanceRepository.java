package com.example.paygenix.repository;

import com.example.paygenix.entity.UserLegalAcceptance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface UserLegalAcceptanceRepository extends JpaRepository<UserLegalAcceptance, Long> {

    // a repeated or concurrent accept of the same version keeps the first row
    @Modifying
    @Query(value = "INSERT INTO user_legal_acceptances (user_id, document_id) VALUES (:userId, :documentId) "
            + "ON CONFLICT (user_id, document_id) DO NOTHING", nativeQuery = true)
    int acceptOnce(Long userId, Long documentId);
}
