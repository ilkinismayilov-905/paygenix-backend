package com.example.paygenix.repository;

import com.example.paygenix.entity.LedgerAccount;
import com.example.paygenix.enums.Currency;
import com.example.paygenix.enums.SystemAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LedgerAccountRepository extends JpaRepository<LedgerAccount, Long> {

    Optional<LedgerAccount> findByCardId(Long cardId);

    Optional<LedgerAccount> findByCodeAndCurrency(SystemAccount code, Currency currency);
}
