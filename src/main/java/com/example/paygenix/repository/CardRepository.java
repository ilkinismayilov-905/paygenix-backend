package com.example.paygenix.repository;

import com.example.paygenix.entity.Cards;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CardRepository extends JpaRepository<Cards, Long> {

    Optional<Cards> findByPanHmac(String panHmac);

    // Counts closed cards too, so a closed card's number is never issued again.
    @Query(value = "SELECT EXISTS (SELECT 1 FROM cards WHERE pan_hmac = :panHmac)", nativeQuery = true)
    boolean existsByPanHmac(String panHmac);

    List<Cards> findByOwnerId(Long ownerId);

    Optional<Cards> findByIdAndOwnerId(Long id, Long ownerId);
}
