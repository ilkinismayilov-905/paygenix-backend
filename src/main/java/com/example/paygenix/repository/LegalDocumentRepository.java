package com.example.paygenix.repository;

import com.example.paygenix.entity.LegalDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LegalDocumentRepository extends JpaRepository<LegalDocument,Long> {
    Optional<LegalDocument> findByTypeAndIsActiveTrue(String type);
}
