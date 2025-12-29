package com.example.paygenix.service;

import com.example.paygenix.dto.request.PaymentRequest;
import com.example.paygenix.dto.request.TransferRequest;
import com.example.paygenix.dto.response.PaymentResponse;
import com.example.paygenix.dto.response.TransferResponse;
import com.example.paygenix.entity.*;
import com.example.paygenix.enums.PaymentStatus;
import com.example.paygenix.enums.TransactionStatus;
import com.example.paygenix.exceptions.InsufficientBalanceException;
import com.example.paygenix.exceptions.NotFoundException;
import com.example.paygenix.mapper.PaymentMapper;
import com.example.paygenix.mapper.PaymentTransactionMapper;
import com.example.paygenix.repository.CardRepository;
import com.example.paygenix.repository.PaymentOnboardingRepository;
import com.example.paygenix.repository.PaymentRepository;
import com.example.paygenix.repository.PaymentTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {
    private final PaymentRepository paymentRepository;
    private final PaymentTransactionRepository ptRepository;
    private final PaymentTransactionMapper ptMapper;
    private final PaymentMapper paymentMapper;
    private final CardRepository cardRepository;
    private final PaymentTransactionService ptService;
    private final PaymentOnboardingRepository poRepository;
    private final PinValidationService pinValidationService;
    private final UserService userService;

    @Transactional
    public String transfer(PaymentRequest req, String email) {

        User user = userService.getByEmail(email);

        Cards from = cardRepository.findByCardNumber(req.fromCardNumber())
                .orElseThrow(() -> new NotFoundException("Card number not found"));
        if(from.getAmount().compareTo(req.amount()) <= 0) {
            throw new InsufficientBalanceException("Amount must be greater than or equal to amount");
        }
        PaymentOnboarding onboarding = PaymentOnboarding.builder()
                .id(UUID.randomUUID().toString())
                .amount(req.amount())
                .fromCardNumber(from.getCardNumber())
                .phoneNumber(req.phoneNumber())
                .creationDate(Instant.now())
                .paymentStatus(PaymentStatus.PENDING)
                .userId(user.getId())
                .build();

       PaymentOnboarding po = poRepository.save(onboarding);
       return onboarding.getId();
    }

    @Transactional
    public PaymentResponse confirmPayment(String flowId,String pin,String username) {
        PaymentOnboarding onboarding = getFlow(flowId,PaymentStatus.PENDING);

        User user = userService.getByEmail(username);

        pinValidationService.validatePin(user.getId(), pin);

        PaymentTransaction transaction = new PaymentTransaction();

        Cards from = cardRepository.findByCardNumber(onboarding.getFromCardNumber())
                .orElseThrow(() -> new NotFoundException("Card number not found"));

        BigDecimal cashbackPercent = transaction.getCashbackPercent();
        BigDecimal cashbackAmount = onboarding.getAmount()
                .multiply(cashbackPercent)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.DOWN);

        BigDecimal newBalance = from.getAmount()
                .subtract(onboarding.getAmount())
                .add(cashbackAmount);

        from.setAmount(newBalance);

        cardRepository.save(from);
        transaction.setTransactionId(ptService.generatePaymentTransactionId());
        transaction.setFrom_card_id(from.getCardNumber());
        transaction.setAmount(onboarding.getAmount());
        transaction.setCashbackPercent(cashbackPercent);
        transaction.setCashbackValue(cashbackAmount);
        transaction.setCurrency(from.getCurrency());
        transaction.setStatus(PaymentStatus.COMPLETED);
        transaction.setPhone_number(onboarding.getPhoneNumber());

        ptRepository.save(transaction);

        Payment payment = Payment.builder()
                .amount(onboarding.getAmount())
                .creationDate(Instant.now())
                .fromCardNumber(from.getCardNumber())
                .phoneNumber(onboarding.getPhoneNumber())
                .build();
        paymentRepository.save(payment);

        onboarding.setPaymentStatus(PaymentStatus.COMPLETED);
        poRepository.save(onboarding);

        return paymentMapper.toPaymentResponse(payment);
    }

    private PaymentOnboarding getFlow(String id, PaymentStatus status) {
        PaymentOnboarding flow = poRepository.findById(id)
                .orElseThrow();

        if (flow.getPaymentStatus() != status)
            throw new RuntimeException("INVALID_STATUS");

        return flow;
    }


}
