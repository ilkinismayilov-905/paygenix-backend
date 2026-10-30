package com.example.paygenix.service;

import com.example.paygenix.dto.request.CardRequest;
import com.example.paygenix.dto.response.CardResponse;
import com.example.paygenix.dto.response.IssuedCardResponse;
import com.example.paygenix.entity.Cards;
import com.example.paygenix.entity.User;
import com.example.paygenix.enums.CardNetwork;
import com.example.paygenix.exceptions.CardHasBalanceException;
import com.example.paygenix.exceptions.NotFoundException;
import com.example.paygenix.mapper.CardConverter;
import com.example.paygenix.repository.CardRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

// Card balances are not cached: they change on every transfer/payment and must be read fresh.
@Service
@RequiredArgsConstructor
@Slf4j
public class CardService {

    private static final int VALID_YEARS = 4;
    private static final int MAX_ISSUE_ATTEMPTS = 5;

    private final CardRepository cardRepository;
    private final CardConverter converter;
    private final PanService panService;
    private final EntityManager entityManager;
    private final LedgerService ledgerService;

    @Transactional
    public IssuedCardResponse issueCard(User owner, CardRequest request) {
        String pan;
        String panHmac;
        int attempts = 0;
        do {
            if (++attempts > MAX_ISSUE_ATTEMPTS) {
                throw new IllegalStateException("Could not generate a unique card number");
            }
            pan = panService.generate();
            panHmac = panService.hmac(pan);
        } while (cardRepository.existsByPanHmac(panHmac));

        Cards card = cardRepository.save(Cards.builder()
                .owner(owner)
                .last4(PanService.last4(pan))
                .panHmac(panHmac)
                .expirationDate(YearMonth.now().plusYears(VALID_YEARS).toString())
                .cardHolderName(owner.getFullname().toUpperCase(Locale.ROOT))
                .cardNetwork(CardNetwork.VISA)
                .currency(request.currency())
                .amount(BigDecimal.ZERO.setScale(2))
                .build());
        ledgerService.openCardAccount(card);
        log.info("Issued card {} for user {}", card.getId(), owner.getId());

        return new IssuedCardResponse(pan, converter.toResponse(card));
    }

    @Transactional(readOnly = true)
    public List<CardResponse> getMyCards(User owner) {
        return converter.toResponse(cardRepository.findByOwnerId(owner.getId()));
    }

    @Transactional(readOnly = true)
    public CardResponse getMyCard(User owner, Long id) {
        return converter.toResponse(ownedCard(owner, id));
    }

    @Transactional(readOnly = true)
    public List<CardResponse> getAllCards() {
        return converter.toResponse(cardRepository.findAll());
    }

    @Transactional
    public void closeCard(User owner, Long id) {
        Cards card = ownedCard(owner, id);
        lockForUpdate(card); // a concurrent credit must not land on a card being deleted
        if (card.getAmount().signum() != 0) {
            throw new CardHasBalanceException();
        }
        card.setClosedAt(Instant.now());
        log.info("Closed card {} for user {}", id, owner.getId());
    }

    // Looks a card up by its full number (e.g. a transfer recipient) without the number ever being stored.
    @Transactional(readOnly = true)
    public Cards findByNumber(String pan) {
        if (pan == null || pan.isBlank()) {
            throw new NotFoundException("Card not found");
        }
        return cardRepository.findByPanHmac(panService.hmac(pan))
                .orElseThrow(() -> new NotFoundException("Card not found"));
    }

    // The caller's own card by full number. Someone else's card gets the same 404 as an unknown number.
    @Transactional(readOnly = true)
    public Cards findOwnedByNumber(User owner, String pan) {
        return cardRepository.findByPanHmacAndOwnerId(panService.hmac(pan), owner.getId())
                .orElseThrow(() -> new NotFoundException("Card not found"));
    }

    /**
     * Locks the cards' rows (SELECT ... FOR UPDATE) and re-reads their balances, always in ascending id
     * order so two transfers between the same cards in opposite directions cannot deadlock.
     * Must run inside the caller's transaction; the locks are released at commit.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockForUpdate(Cards... cards) {
        try {
            Arrays.stream(cards)
                    .sorted(Comparator.comparing(Cards::getId))
                    .forEach(card -> entityManager.refresh(card, LockModeType.PESSIMISTIC_WRITE));
        } catch (EntityNotFoundException closedMeanwhile) {
            throw new NotFoundException("Card not found");
        }
    }

    // Another user's card answers 404, so ids cannot be probed.
    private Cards ownedCard(User owner, Long id) {
        return cardRepository.findByIdAndOwnerId(id, owner.getId())
                .orElseThrow(() -> new NotFoundException("Card not found"));
    }
}
