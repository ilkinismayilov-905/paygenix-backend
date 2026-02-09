package com.example.paygenix.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "legal_documents")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class LegalDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String type; // TERMS, PRIVACY
    private String version;
    private LocalDate effectiveDate;

    @Column(columnDefinition = "text")
    private String content;

    private boolean isActive;
}

