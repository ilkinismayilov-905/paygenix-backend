package com.example.paygenix.service;

import com.example.paygenix.entity.Cards;
import com.example.paygenix.entity.LedgerAccount;
import com.example.paygenix.entity.LedgerEntry;
import com.example.paygenix.enums.Currency;
import com.example.paygenix.enums.LedgerKind;
import com.example.paygenix.enums.SystemAccount;
import com.example.paygenix.repository.LedgerAccountRepository;
import com.example.paygenix.repository.LedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * The only place card balances change. A posting is a set of legs in one currency that sums to zero;
 * card legs also move the card's cached balance, so cards.amount always equals its ledger balance.
 * Callers lock the card rows first (CardService.lockForUpdate).
 */
@Service
@RequiredArgsConstructor
public class LedgerService {

    private final LedgerAccountRepository accountRepository;
    private final LedgerEntryRepository entryRepository;

    /** One side of a posting: either a card or a system account. Positive raises its balance. */
    public record Leg(Cards card, SystemAccount system, BigDecimal amount) {
        public static Leg card(Cards card, BigDecimal amount) {
            return new Leg(card, null, amount);
        }

        public static Leg system(SystemAccount account, BigDecimal amount) {
            return new Leg(null, account, amount);
        }
    }

    @Transactional
    public LedgerAccount openCardAccount(Cards card) {
        return accountRepository.save(LedgerAccount.builder().cardId(card.getId()).currency(card.getCurrency()).build());
    }

    /** Returns the posting id, or null when every leg is zero and nothing was booked. */
    @Transactional(propagation = Propagation.MANDATORY)
    public UUID post(LedgerKind kind, String reference, Currency currency, List<Leg> allLegs) {
        // a fee or cashback that rounds to 0.00 is simply not booked
        List<Leg> legs = allLegs.stream().filter(leg -> leg.amount().signum() != 0).toList();
        if (legs.isEmpty()) {
            return null; // nothing moves, e.g. a 0.00 cashback
        }
        if (legs.size() < 2) {
            throw new IllegalStateException("A posting needs at least two legs");
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (Leg leg : legs) {
            if (leg.amount().stripTrailingZeros().scale() > 2) {
                throw new IllegalStateException("Ledger amounts can have at most 2 decimals");
            }
            if (leg.card() != null && leg.card().getCurrency() != currency) {
                throw new IllegalStateException("All legs of a posting must share one currency");
            }
            sum = sum.add(leg.amount());
        }
        if (sum.signum() != 0) {
            throw new IllegalStateException("Posting does not balance: " + sum);
        }

        UUID txId = UUID.randomUUID();
        for (Leg leg : legs) {
            if (leg.card() != null) {
                leg.card().setAmount(leg.card().getAmount().add(leg.amount()));
            }
            entryRepository.save(LedgerEntry.builder()
                    .txId(txId)
                    .accountId(account(leg, currency).getId())
                    .amount(leg.amount())
                    .currency(currency)
                    .kind(kind)
                    .reference(reference)
                    .build());
        }
        return txId;
    }

    @Transactional(readOnly = true)
    public BigDecimal balanceOf(Cards card) {
        return entryRepository.balanceOf(account(Leg.card(card, BigDecimal.ONE), card.getCurrency()).getId());
    }

    private LedgerAccount account(Leg leg, Currency currency) {
        if (leg.card() != null) {
            return accountRepository.findByCardId(leg.card().getId())
                    .orElseThrow(() -> new IllegalStateException("Card " + leg.card().getId() + " has no ledger account"));
        }
        return accountRepository.findByCodeAndCurrency(leg.system(), currency)
                .orElseThrow(() -> new IllegalStateException("Missing system account " + leg.system() + " " + currency));
    }
}
