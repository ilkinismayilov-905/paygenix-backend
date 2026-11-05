package com.example.paygenix.entity;

import com.example.paygenix.enums.Currency;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "transfer")
@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class Transfer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String fromCardNumber;
    private String toCardNumber;
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    private Currency currency; // null on rows from before V15

    @CreationTimestamp
    private Instant creationDate;
}
