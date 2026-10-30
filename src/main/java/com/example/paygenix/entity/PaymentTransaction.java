package com.example.paygenix.entity;

import com.example.paygenix.enums.Currency;
import com.example.paygenix.enums.PaymentStatus;
import com.example.paygenix.enums.TransactionStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "payment_transactions")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PaymentTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String transactionId;

    @Column(nullable = false)
    private String from_card_id;

    @Column(nullable = false)
    private String phone_number;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false, precision = 3, scale = 1)
    private BigDecimal cashbackPercent = new BigDecimal("0.5");

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal cashbackValue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Currency currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @CreationTimestamp
    private Instant creationDate;


}
