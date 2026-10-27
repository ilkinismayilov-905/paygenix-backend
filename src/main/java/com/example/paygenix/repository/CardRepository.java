package com.example.paygenix.repository;

import com.example.paygenix.entity.Cards;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CardRepository extends JpaRepository<Cards, Long> {

    Optional<Cards> findByPanHmac(String panHmac);

    boolean existsByPanHmac(String panHmac);

    List<Cards> findByOwnerId(Long ownerId);

    Optional<Cards> findByIdAndOwnerId(Long id, Long ownerId);
}
