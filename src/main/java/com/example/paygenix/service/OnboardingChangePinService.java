package com.example.paygenix.service;

import com.example.paygenix.entity.OnboardingChangePin;
import com.example.paygenix.entity.OnboardingFlow;
import com.example.paygenix.entity.User;
import com.example.paygenix.exceptions.ApiException;
import com.example.paygenix.exceptions.NotFoundException;
import com.example.paygenix.repository.OnboardingChangePinRepository;
import com.example.paygenix.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class OnboardingChangePinService {

    private static final Duration OTP_TTL = Duration.ofSeconds(300);          // 5 dəqiqə
    private static final Duration FLOW_TTL = Duration.ofMinutes(15);
    private static final Duration SEND_COOLDOWN = Duration.ofSeconds(60);     // 60 saniyə
    private static final int MAX_VERIFY_ATTEMPTS = 5;

    private static final String KEY_SEND_LOCK_PREFIX = "otp:send:";
    private static final String KEY_ATTEMPTS_PREFIX = "otp:attempts:";

    private final UserRepository userRepo;
    private final PasswordEncoder encoder;
    private final JwtService jwtService;
    private final EmailService emailService;
    private final OnboardingChangePinRepository changePinRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    @Transactional
    public String submitEmail(String email) {

        if (!userRepo.existsByEmail(email)) {
            throw new NotFoundException("User not found");
        }

        enforceSendCoolDown(email);

        String otp = OtpGenerator.newOtp();
        OnboardingChangePin flow = OnboardingChangePin.builder()
                .id(UUID.randomUUID().toString())
                .email(email)
                .step(OnboardingChangePin.Step.OTP_SENT)
                .otpHash(encoder.encode(otp))
                .otpExpiresAt(Instant.now().plus(OTP_TTL))
                .build();

        changePinRepository.save(flow);

        initAttempts(flow.getId());

        emailService.sendOtp(email, otp);
        log.info("Email sent");
        return flow.getId();
    }

    @Transactional
    public void verifyOtp(String flowId, String callerEmail, String otp) {
        OnboardingChangePin flow = getFlow(flowId, callerEmail, OnboardingChangePin.Step.OTP_SENT);

        if (Instant.now().isAfter(flow.getOtpExpiresAt())) {
            clearAttempts(flowId);
            throw ApiException.otpExpired();
        }

        int attempts = incrementAttemptsOrThrow(flowId);

        if (!encoder.matches(otp, flow.getOtpHash())) {
            if (attempts >= MAX_VERIFY_ATTEMPTS) {
                // T-138: keep the counter (it lives as long as the code), so later tries stay refused
                throw ApiException.tooManyAttempts();
            }
            throw ApiException.invalidOtp();
        }

        clearAttempts(flowId);

        flow.setOtpHash(null);
        flow.setOtpExpiresAt(null);
        flow.setStep(OnboardingChangePin.Step.OTP_VERIFIED);
        log.info("OTP VERIFIED");
    }

    @Transactional
    public void changePin(String flowId, String callerEmail, String newPin, String reEnterNewPin) {
        OnboardingChangePin flow = getFlow(flowId, callerEmail, OnboardingChangePin.Step.OTP_VERIFIED);

        User user = userRepo.findByEmail(flow.getEmail())
                .orElseThrow(() -> new NotFoundException("User not found"));

        if (newPin == null || reEnterNewPin == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Both PIN fields are required");
        }

        // mismatch first, so a wrong pair never reveals whether one of them is the current PIN (T-140)
        if (!newPin.equals(reEnterNewPin)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PIN_MISMATCH", "The two PINs do not match");
        }

        if (encoder.matches(newPin, user.getPinHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PIN_UNCHANGED", "The new PIN must differ from the current one");
        }

        user.setPinHash(encoder.encode(newPin));
        userRepo.save(user);

        flow.setStep(OnboardingChangePin.Step.COMPLETED);
        log.info("PIN CHANGED");
    }

    // SEC-20: only the user who started the flow may continue it, and only for FLOW_TTL
    private OnboardingChangePin getFlow(String id, String callerEmail, OnboardingChangePin.Step step) {
        OnboardingChangePin flow = changePinRepository.findById(id)
                .filter(f -> f.getEmail().equalsIgnoreCase(callerEmail))
                .orElseThrow(() -> new NotFoundException("PIN change flow not found"));

        if (flow.getStep() != step)
            throw ApiException.invalidStep();

        if (flow.getCreatedAt().plus(FLOW_TTL).isBefore(Instant.now()))
            throw ApiException.flowExpired();

        return flow;
    }

    private void enforceSendCoolDown(String email) {
        String key = KEY_SEND_LOCK_PREFIX + normalizeEmail(email);
        ValueOperations<String, Object> ops = redisTemplate.opsForValue();

        Boolean ok = ops.setIfAbsent(key, "1", SEND_COOLDOWN);
        if (ok == null || !ok) {
            throw ApiException.otpSendTooFrequent(SEND_COOLDOWN);
        }

    }

    private void initAttempts(String flowId) {
        String key = KEY_ATTEMPTS_PREFIX + flowId;
        redisTemplate.opsForValue().set(key, 0, OTP_TTL);
    }

    private int incrementAttemptsOrThrow(String flowId) {
        String key = KEY_ATTEMPTS_PREFIX + flowId;

        Long val = redisTemplate.opsForValue().increment(key);

        if (val == null) {
            redisTemplate.opsForValue().set(key, 1, OTP_TTL);
            return 1;
        }

        Long ttl = redisTemplate.getExpire(key);
        if (ttl == null || ttl <= 0) {
            redisTemplate.expire(key, OTP_TTL);
        }

        if (val > MAX_VERIFY_ATTEMPTS) {
            throw ApiException.tooManyAttempts();
        }

        return val.intValue();

    }

    private void clearAttempts(String flowId) {
        redisTemplate.delete(KEY_ATTEMPTS_PREFIX + flowId);
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : User.normalizeEmail(email);
    }

}
