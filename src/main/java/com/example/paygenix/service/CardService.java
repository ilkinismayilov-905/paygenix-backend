package com.example.paygenix.service;


import com.example.paygenix.mapper.CardConverter;
import com.example.paygenix.mapper.CardMapper;
import com.example.paygenix.dto.request.CardRequest;
import com.example.paygenix.dto.response.CardResponse;
import com.example.paygenix.entity.Cards;
import com.example.paygenix.exceptions.NotFoundException;
import com.example.paygenix.repository.CardRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CardService {
    private final CardRepository cardRepository;
    private final CardConverter converter;
//    private final CardMapper mapper;

    @Transactional
    public CardResponse addCard(CardRequest card) {
        log.info("Adding card {}", card);
        Cards c =  converter.toCards(card);

        if (c.getAmount() == null) {
            c.setAmount(BigDecimal.ZERO.setScale(2));
        }

        Cards savedCard = cardRepository.save(c);
        log.info("Saved Card {}", savedCard.getId());

        return converter.toResponse(savedCard);
    }

    @Transactional(readOnly = true)
    public CardResponse getCardByNumber(String number) {
        log.info("Getting card by number {}", number);
        Cards c = cardRepository.findByCardNumber(number)
                .orElseThrow(() -> new NotFoundException("Card not found by cardNumber"));
        return converter.toResponse(c);
    }

    @Transactional(readOnly = true)
    public List<CardResponse> getAllCards() {
        log.info("Getting all cards");
        List<Cards> cards = cardRepository.findAll();
        return converter.toResponse(cards);
    }

    @Transactional
    public CardResponse updateCard(Long id,CardRequest card) {
        log.info("Updating card {}", card);
        Cards cards = cardRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Card not found by id"));
        converter.update(card, cards);

        Cards savedCard = cardRepository.save(cards);
        return converter.toResponse(savedCard);
    }
    @Transactional
    public void deleteCard(Long id) {
        log.info("Deleting card {}", id);

        cardRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public CardResponse findById(Long id) {
        log.info("Getting card by id {}", id);
        Cards cards = cardRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Card not found by id"));
        return converter.toResponse(cards);
    }

    @Transactional
    public CardResponse addAmount(String cardNumber, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than 0");
        }
        log.info("Adding amount {}", amount);
        Cards cards = cardRepository.findByCardNumber(cardNumber)
                .orElseThrow(() -> new NotFoundException("Card not found by cardNumber"));
        BigDecimal newAmount = cards.getAmount()
                .add(amount)
                .setScale(2, RoundingMode.HALF_UP);
        cards.setAmount(newAmount);
        cardRepository.save(cards);
        return converter.toResponse(cards);
    }
}
