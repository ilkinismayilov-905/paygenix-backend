package com.example.paygenix.repository;

import com.example.paygenix.entity.LegalDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LegalDocumentRepository extends JpaRepository<LegalDocument,Long> {
    Optional<LegalDocument> findByTypeAndIsActiveTrue(String type);

    /** Active documents whose current version the user has not accepted. */
    @Query("SELECT d FROM LegalDocument d WHERE d.isActive = true AND NOT EXISTS "
            + "(SELECT a.id FROM UserLegalAcceptance a WHERE a.userId = :userId AND a.documentId = d.id) ORDER BY d.type")
    List<LegalDocument> findPendingFor(Long userId);
}
