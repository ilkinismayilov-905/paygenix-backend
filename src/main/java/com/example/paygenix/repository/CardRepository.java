package com.example.paygenix.repository;

import com.example.paygenix.entity.Cards;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

import static jakarta.persistence.LockModeType.PESSIMISTIC_WRITE;


@Repository
public interface CardRepository extends JpaRepository<Cards, Long> {
    @Lock(PESSIMISTIC_WRITE)
    Optional<Cards> findByCardNumber(String cardNumber);


//    @Query("select c from Cards c where c.cardNumber = :cardNumber")
//    Cards findByCardNumberForUpdate(String number);
}
