package com.example.paygenix.service;

import com.example.paygenix.cache.CacheNames;
import com.example.paygenix.dto.response.LegalDocumentResponse;
import com.example.paygenix.exceptions.NotFoundException;
import com.example.paygenix.repository.LegalDocumentRepository;
import lombok.RequiredArgsConstructor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
@Slf4j
public class LegalDocumentService {
    private final LegalDocumentRepository repository;

    @Transactional(readOnly = true)
    @Cacheable(
            cacheNames = CacheNames.LEGAL_DOCUMENT_BY_TYPE,
            key = "#type"
    )
    public LegalDocumentResponse getActiveDocument(String type) {
        log.info("Getting active document for type {}", type);
        return repository.findByTypeAndIsActiveTrue(type)
                .map(doc -> new LegalDocumentResponse(doc.getType(), doc.getVersion(), doc.getEffectiveDate(), doc.getContent()))
                .orElseThrow(() -> new NotFoundException("Document not found"));
    }
}
