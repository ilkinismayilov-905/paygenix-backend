package com.example.paygenix.entity;

import com.example.paygenix.enums.CardNetwork;
import com.example.paygenix.enums.Currency;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Cards {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false,unique = true)
    private String cardNumber;

    @Column(nullable = false)
    private String expirationDate;

    @Column(nullable = false)
    private String cardHolderName;

    @Column(nullable = false,unique = true)
    private String cvv;

    @Enumerated(EnumType.STRING)
    private CardNetwork cardNetwork;

    @Enumerated(EnumType.STRING)
    private Currency currency;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    public Cards(String cardNumber, String expirationDate, String cardHolderName, String cvv, CardNetwork cardNetwork, Currency currency) {
        this.cardNumber = cardNumber;
        this.expirationDate = expirationDate;
        this.cardHolderName = cardHolderName;
        this.cvv = cvv;
        this.cardNetwork = cardNetwork;
        this.currency = currency;
    }
}
