package com.example.paygenix.entity;

import com.example.paygenix.enums.PaymentStatus;
import com.example.paygenix.enums.Currency;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "payment_onboarding")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PaymentOnboarding {
    @Id
    private String id;

    private String phoneNumber;
    private Long fromCardId;
    private String fromCardNumber; // last four digits only
    private BigDecimal amount;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Currency currency;
    private Long userId;
    @Enumerated(value = EnumType.STRING)
    private PaymentStatus paymentStatus;

    @CreationTimestamp
    private Instant creationDate;
}
