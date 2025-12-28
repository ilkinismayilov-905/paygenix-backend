package com.example.paygenix.repository;

import com.example.paygenix.entity.Transactions;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transactions, Long> {
    Optional<List<Transactions>> findByCreationDateBetween(Instant startDate, Instant endDate);
}
