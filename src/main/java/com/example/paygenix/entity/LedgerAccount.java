package com.example.paygenix.entity;

import com.example.paygenix.enums.Currency;
import com.example.paygenix.enums.SystemAccount;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "ledger_accounts")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private SystemAccount code;

    @Column(name = "card_id")
    private Long cardId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Currency currency;
}
