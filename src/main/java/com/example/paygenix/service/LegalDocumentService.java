package com.example.paygenix.service;

import com.example.paygenix.entity.LegalDocument;
import com.example.paygenix.repository.LegalDocumentRepository;
import lombok.RequiredArgsConstructor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
@Slf4j
public class LegalDocumentService {
    private final LegalDocumentRepository repository;


    public LegalDocument getActiveDocument(String type) {
        log.info("Getting active document for type {}", type);
        return repository.findByTypeAndIsActiveTrue(type)
                .orElseThrow(() -> new RuntimeException("Document not found"));
    }
}
