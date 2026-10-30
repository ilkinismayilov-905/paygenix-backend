package com.example.paygenix.service;

import com.example.paygenix.mapper.TransferMapper;
import com.example.paygenix.dto.request.TransferRequest;
import com.example.paygenix.dto.response.TransferResponse;
import com.example.paygenix.entity.Cards;
import com.example.paygenix.entity.Transactions;
import com.example.paygenix.entity.Transfer;
import com.example.paygenix.enums.TransactionStatus;
import com.example.paygenix.entity.User;
import com.example.paygenix.exceptions.InsufficientBalanceException;
import com.example.paygenix.exceptions.NotFoundException;
import com.example.paygenix.exceptions.TransferRejectedException;
import com.example.paygenix.repository.CardRepository;
import com.example.paygenix.repository.TransactionRepository;
import com.example.paygenix.repository.TransferRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransferService {
    private final TransferRepository transferRepository;
    private final CardRepository cardRepository;
    private final TransactionService transactionService;
    private final CardService cardService;
    private final PinValidationService pinValidationService;
    private final TransactionRepository transactionRepository;
    private final TransferMapper mapper;

    @Transactional
    public TransferResponse transfer(User caller, TransferRequest req) {
        Transactions transaction = new Transactions();

        pinValidationService.validatePin(caller.getId(), req.pin());

        Cards from = cardService.findOwnedByNumber(caller, req.fromCardNumber());
        Cards to   = cardService.findByNumber(req.toCardNumber());
        if (from.getId().equals(to.getId())) {
            throw TransferRejectedException.sameCard();
        }
        if (from.getCurrency() != to.getCurrency()) {
            throw TransferRejectedException.currencyMismatch();
        }
        // balances are read only after the rows are locked, so concurrent debits cannot overspend
        cardService.lockForUpdate(from, to);

        BigDecimal ratePercent = transaction.getCommissionRate();
        BigDecimal commissionAmount = req.amount()
                .multiply(ratePercent)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal totalAmount = commissionAmount.add(req.amount());
        if (from.getAmount().compareTo(totalAmount) < 0) {
            throw new InsufficientBalanceException("Insufficient balance for amount plus commission");
        }

        from.setAmount(from.getAmount().subtract(totalAmount));
        to.setAmount(to.getAmount().add(req.amount()));

        cardRepository.save(from);
        cardRepository.save(to);
        transaction.setAmount(req.amount());
        transaction.setFrom_card_id(from.getLast4());
        transaction.setTo_card_id(to.getLast4());
        transaction.setCurrency(from.getCurrency());
        transaction.setTransactionId(transactionService.generateTransactionId());
        transaction.setStatus(TransactionStatus.COMPLETED);
        transaction.setCommissionValue(commissionAmount);
        transaction.setTotalAmount(totalAmount);

        transactionRepository.save(transaction);

        Transfer transfer = mapper.mapToTransfer(req);
        // history keeps only the last four digits, never the full number from the request
        transfer.setFromCardNumber(from.getLast4());
        transfer.setToCardNumber(to.getLast4());
        transferRepository.save(transfer);

        return mapper.mapToTransferResponse(transfer);
    }

    public List<TransferResponse> getAllTransfers() {
        log.info("Getting all transfers");
        List<Transfer> transfers = transferRepository.findAll();
        return mapper.mapToTransferResponseList(transfers);
    }

    public TransferResponse getTransferById(Long id) {
        log.info("Getting transfer with id {}", id);
        Transfer transfer = transferRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Transfer not found"));
        return mapper.mapToTransferResponse(transfer);
    }
}
