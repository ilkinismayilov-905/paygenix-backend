package com.example.paygenix.service;

import com.example.paygenix.dto.request.PaymentRequest;
import com.example.paygenix.dto.response.PaymentResponse;
import com.example.paygenix.entity.Cards;
import com.example.paygenix.entity.Payment;
import com.example.paygenix.entity.PaymentOnboarding;
import com.example.paygenix.entity.PaymentTransaction;
import com.example.paygenix.enums.PaymentStatus;
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
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private static final String KEY_CONFIRM_LOCK_PREFIX = "pay:confirm:lock:";
    private static final Duration CONFIRM_LOCK_TTL = Duration.ofSeconds(60);

    private final PaymentRepository paymentRepository;
    private final PaymentTransactionRepository ptRepository;
    private final PaymentTransactionMapper ptMapper;
    private final PaymentMapper paymentMapper;
    private final CardRepository cardRepository;
    private final CardService cardService;
    private final PaymentTransactionService ptService;
    private final PaymentOnboardingRepository poRepository;
    private final PinValidationService pinValidationService;
    private final UserService userService;
    private final RedisTemplate<String, Object> redisTemplate;

    @Transactional
    public String transfer(PaymentRequest req, String email) {
        var user = userService.getByEmail(email);

        Cards from = cardService.findByNumber(req.fromCardNumber());

        if (from.getAmount().compareTo(req.amount()) < 0) {
            throw new InsufficientBalanceException("Insufficient balance");
        }

        PaymentOnboarding onboarding = PaymentOnboarding.builder()
                .id(UUID.randomUUID().toString())
                .amount(req.amount())
                .fromCardId(from.getId())
                .fromCardNumber(from.getLast4())
                .phoneNumber(req.phoneNumber())
                .creationDate(Instant.now())
                .paymentStatus(PaymentStatus.PENDING)
                .userId(user.getId())
                .build();

        poRepository.save(onboarding);
        return onboarding.getId();
    }

    @Transactional
    public PaymentResponse confirmPayment(String flowId, String pin, String username) {
        String lockKey = KEY_CONFIRM_LOCK_PREFIX + flowId;

        if (!acquireLock(lockKey, CONFIRM_LOCK_TTL)) {
            throw new RuntimeException("PAYMENT_ALREADY_PROCESSING");
        }

        try {
            PaymentOnboarding onboarding = getFlow(flowId, PaymentStatus.PENDING);

            var user = userService.getByEmail(username);
            pinValidationService.validatePin(user.getId(), pin);

            Cards from = cardRepository.findById(onboarding.getFromCardId())
                    .orElseThrow(() -> new NotFoundException("Card not found"));

            if (from.getAmount().compareTo(onboarding.getAmount()) < 0) {
                throw new InsufficientBalanceException("Insufficient balance");
            }

            PaymentTransaction transaction = new PaymentTransaction();

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
            transaction.setFrom_card_id(from.getLast4());
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
                    .fromCardNumber(from.getLast4())
                    .phoneNumber(onboarding.getPhoneNumber())
                    .build();
            paymentRepository.save(payment);

            onboarding.setPaymentStatus(PaymentStatus.COMPLETED);
            poRepository.save(onboarding);

            return paymentMapper.toPaymentResponse(payment);
        } finally {
            releaseLock(lockKey);
        }
    }

    private boolean acquireLock(String key, Duration ttl) {
        Boolean ok = redisTemplate.opsForValue().setIfAbsent(key, "1", ttl);
        return ok != null && ok;
    }

    private void releaseLock(String key) {
        redisTemplate.delete(key);
    }

    private PaymentOnboarding getFlow(String id, PaymentStatus status) {
        PaymentOnboarding flow = poRepository.findById(id).orElseThrow();

        if (flow.getPaymentStatus() != status) {
            throw new RuntimeException("INVALID_STATUS");
        }

        return flow;
    }
}