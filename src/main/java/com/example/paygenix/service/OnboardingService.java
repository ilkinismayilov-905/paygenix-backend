package com.example.paygenix.service;

import com.example.paygenix.entity.OnboardingFlow;
import com.example.paygenix.entity.User;
import com.example.paygenix.enums.Role;
import com.example.paygenix.repository.OnboardingFlowRepository;
import com.example.paygenix.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class OnboardingService {

    private static final Duration OTP_TTL = Duration.ofSeconds(300);           // 5 dəq
    private static final Duration FLOW_TTL = Duration.ofSeconds(1800);         // 30 dəq
    private static final Duration SEND_COOLDOWN = Duration.ofSeconds(60);      // 60s
    private static final int MAX_VERIFY_ATTEMPTS = 5;

    private static final String KEY_SEND_LOCK_PREFIX = "onb:otp:send:";
    private static final String KEY_ATTEMPTS_PREFIX = "onb:otp:attempts:";

    private final OnboardingFlowRepository flowRepo;
    private final UserRepository userRepo;
    private final PasswordEncoder encoder;
    private final JwtService jwtService;
    private final EmailService emailService;
    private final RedisTemplate<String, Object> redisTemplate;

    @Transactional
    public String submitEmail(String email) {
        if (userRepo.existsByEmail(email)) {
            throw new RuntimeException("User with email " + email + " already exists");
        }

        enforceSendCooldown(email);

        String otp = generateOtp();

        OnboardingFlow flow = OnboardingFlow.builder()
                .id(UUID.randomUUID().toString())
                .email(email)
                .step(OnboardingFlow.Step.EMAIL_SUBMITTED)
                .otpHash(encoder.encode(otp))
                .otpExpiresAt(Instant.now().plusSeconds(OTP_TTL.toSeconds()))
                .expiresAt(Instant.now().plusSeconds(FLOW_TTL.toSeconds()))
                .build();

        flowRepo.save(flow);
        initAttempts(flow.getId());

        emailService.sendOtp(email, otp);
        return flow.getId();
    }

    @Transactional
    public String verifyOtp(String flowId, String otp) {
        OnboardingFlow flow = getFlow(flowId, OnboardingFlow.Step.EMAIL_SUBMITTED);

        if (Instant.now().isAfter(flow.getOtpExpiresAt())) {
            clearAttempts(flowId);
            throw new RuntimeException("OTP_EXPIRED");
        }

        int attempts = incrementAttemptsOrThrow(flowId);

        if (!encoder.matches(otp, flow.getOtpHash())) {
            if (attempts >= MAX_VERIFY_ATTEMPTS) {
                clearAttempts(flowId);
                throw new RuntimeException("TOO_MANY_ATTEMPTS");
            }
            throw new RuntimeException("INVALID_OTP");
        }

        clearAttempts(flowId);

        flow.setStep(OnboardingFlow.Step.OTP_VERIFIED);
        return jwtService.generateOnboardingToken(flow);
    }

    @Transactional
    public void setFullName(String flowId, String fullName) {
        OnboardingFlow flow = getFlow(flowId, OnboardingFlow.Step.OTP_VERIFIED);
        flow.setFullName(fullName);
        flow.setStep(OnboardingFlow.Step.FULLNAME_SET);
    }

    @Transactional
    public void setUsername(String flowId, String username) {
        if (userRepo.existsByUsername(username)) {
            throw new RuntimeException("User with name " + username + " already exists");
        }

        OnboardingFlow flow = getFlow(flowId, OnboardingFlow.Step.FULLNAME_SET);
        flow.setUsernameCandidate(username);
        flow.setStep(OnboardingFlow.Step.USERNAME_SET);
    }

    @Transactional
    public void setPin(String flowId, String pin) {
        OnboardingFlow flow = getFlow(flowId, OnboardingFlow.Step.USERNAME_SET);

        User user = User.builder()
                .email(flow.getEmail())
                .fullname(flow.getFullName())
                .username(flow.getUsernameCandidate())
                .pinHash(encoder.encode(pin))
                .status(User.Status.ACTIVE)
                .roles(Set.of(Role.USER_ROLE))
                .build();

        userRepo.save(user);

        flow.setStep(OnboardingFlow.Step.COMPLETED);
    }

    private String generateOtp() {
        return String.valueOf(ThreadLocalRandom.current().nextInt(100000, 999999));
    }

    private OnboardingFlow getFlow(String id, OnboardingFlow.Step step) {
        OnboardingFlow flow = flowRepo.findById(id).orElseThrow();

        if (flow.getStep() != step) {
            throw new RuntimeException("INVALID_STEP");
        }

        if (flow.getExpiresAt() != null && Instant.now().isAfter(flow.getExpiresAt())) {
            throw new RuntimeException("FLOW_EXPIRED");
        }

        return flow;
    }

    // ------------------- REDIS RATE LIMIT / ATTEMPT LIMIT -------------------

    private void enforceSendCooldown(String email) {
        String key = KEY_SEND_LOCK_PREFIX + normalizeEmail(email);
        ValueOperations<String, Object> ops = redisTemplate.opsForValue();

        Boolean ok = ops.setIfAbsent(key, "1", SEND_COOLDOWN);
        if (ok == null || !ok) {
            throw new RuntimeException("OTP_SEND_TOO_FREQUENT");
        }
    }

    private void initAttempts(String flowId) {
        String key = KEY_ATTEMPTS_PREFIX + flowId;
        redisTemplate.opsForValue().set(key, 0, OTP_TTL);
    }

    private int incrementAttemptsOrThrow(String flowId) {
        String key = KEY_ATTEMPTS_PREFIX + flowId;
        ValueOperations<String, Object> ops = redisTemplate.opsForValue();

        Long val = ops.increment(key);
        if (val == null) {
            ops.set(key, 1, OTP_TTL);
            return 1;
        }

        Long ttl = redisTemplate.getExpire(key);
        if (ttl == null || ttl <= 0) {
            redisTemplate.expire(key, OTP_TTL);
        }

        if (val > MAX_VERIFY_ATTEMPTS) {
            throw new RuntimeException("TOO_MANY_ATTEMPTS");
        }

        return val.intValue();
    }

    private void clearAttempts(String flowId) {
        redisTemplate.delete(KEY_ATTEMPTS_PREFIX + flowId);
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }
}