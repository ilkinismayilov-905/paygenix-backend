package com.example.paygenix.service;

import com.example.paygenix.cache.CacheNames;
import com.example.paygenix.entity.User;
import com.example.paygenix.exceptions.ApiException;
import com.example.paygenix.exceptions.NotFoundException;
import com.example.paygenix.repository.EditProfileRepository;
import com.example.paygenix.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Email change (SEC-14): the new address must prove it can receive mail before it replaces the old one, so nobody
 * can claim an address they do not own. The pending change lives in Redis for 10 minutes with at most 5 OTP tries.
 * Codes can be requested once a minute and 5 times a day per user, so restarting cannot be used to keep guessing.
 * The email is the login name and the token subject, so a confirmed change signs out every device.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailChangeService {

    static final Duration TTL = Duration.ofMinutes(10);
    static final int MAX_ATTEMPTS = 5;
    static final Duration SEND_COOLDOWN = Duration.ofSeconds(60);
    static final int MAX_STARTS_PER_DAY = 5;
    private static final String KEY_PREFIX = "email-change:";
    private static final String COOLDOWN_PREFIX = "email-change:cooldown:";
    private static final String DAILY_PREFIX = "email-change:daily:";

    private final UserRepository userRepository;
    private final EditProfileRepository editProfileRepository;
    private final StringRedisTemplate redis;
    private final PasswordEncoder encoder;
    private final EmailService emailService;
    private final SessionService sessionService;

    public void start(Long userId, String newEmail) {
        if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(COOLDOWN_PREFIX + userId, "1", SEND_COOLDOWN))) {
            throw ApiException.otpSendTooFrequent(SEND_COOLDOWN);
        }
        Long startsToday = redis.opsForValue().increment(DAILY_PREFIX + userId);
        if (startsToday != null && startsToday == 1) {
            redis.expire(DAILY_PREFIX + userId, Duration.ofDays(1));
        }
        if (startsToday == null || startsToday > MAX_STARTS_PER_DAY) {
            Long ttl = redis.getExpire(DAILY_PREFIX + userId);
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "EMAIL_CHANGE_LIMIT", "Too many email change requests today",
                    ttl != null && ttl > 0 ? Duration.ofSeconds(ttl) : Duration.ofDays(1));
        }
        // checked after the rate limits, so probing which addresses are registered costs the same as a real change
        if (userRepository.existsByEmail(newEmail) || userRepository.existsByUsername(newEmail)) {
            throw emailTaken();
        }
        String otp = OtpGenerator.newOtp();
        String key = KEY_PREFIX + userId;
        redis.delete(key);
        redis.opsForHash().putAll(key, Map.of("email", newEmail, "otpHash", encoder.encode(otp), "attempts", "0"));
        redis.expire(key, TTL);
        emailService.sendOtp(newEmail, otp);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.EDIT_PROFILE_BY_ID, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.EDIT_PROFILE_ALL, allEntries = true)
    })
    public void confirm(Long userId, String otp) {
        String key = KEY_PREFIX + userId;
        // counted before the check, so parallel guesses cannot exceed the limit
        long attempts = redis.opsForHash().increment(key, "attempts", 1);
        List<Object> pending = redis.opsForHash().multiGet(key, List.of("email", "otpHash"));
        if (pending.get(0) == null) {
            redis.delete(key); // the increment recreated an expired key without a TTL
            throw new ApiException(HttpStatus.BAD_REQUEST, "OTP_EXPIRED", "No pending email change, or it expired");
        }
        if (attempts > MAX_ATTEMPTS) {
            redis.delete(key);
            throw ApiException.tooManyAttempts();
        }
        if (!encoder.matches(otp, (String) pending.get(1))) {
            throw ApiException.invalidOtp();
        }
        redis.delete(key);

        String newEmail = (String) pending.get(0);
        if (userRepository.existsByEmail(newEmail) || userRepository.existsByUsername(newEmail)) {
            throw emailTaken();
        }
        User user = userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
        editProfileRepository.findByUserId(userId).ifPresent(profile -> profile.setEmail(newEmail));
        if (user.getProvider() == User.AuthProvider.GOOGLE && user.getUsername().equals(user.getEmail())) {
            user.setUsername(newEmail); // Google accounts use the email as username; keep them in step
        }
        user.setEmail(newEmail);
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // another account confirmed the same address between the check and the update
            throw emailTaken();
        }
        sessionService.revokeAll(userId);
        log.info("Email changed for user {}", userId);
    }

    private static ApiException emailTaken() {
        return new ApiException(HttpStatus.CONFLICT, "EMAIL_TAKEN", "Email is already taken");
    }
}
