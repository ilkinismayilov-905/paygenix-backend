package com.example.paygenix.service;

import com.example.paygenix.cache.CacheNames;
import com.example.paygenix.entity.User;
import com.example.paygenix.exceptions.NotFoundException;
import com.example.paygenix.repository.EditProfileRepository;
import com.example.paygenix.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Email change (SEC-14): the new address must prove it can receive mail before it replaces the old one, so nobody
 * can claim an address they do not own. The pending change lives in Redis for 10 minutes with at most 5 OTP tries.
 * The email is the login name and the token subject, so a confirmed change signs out every device.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailChangeService {

    static final Duration TTL = Duration.ofMinutes(10);
    static final int MAX_ATTEMPTS = 5;
    private static final String KEY_PREFIX = "email-change:";

    private final UserRepository userRepository;
    private final EditProfileRepository editProfileRepository;
    private final StringRedisTemplate redis;
    private final PasswordEncoder encoder;
    private final EmailService emailService;
    private final SessionService sessionService;

    public void start(Long userId, String newEmail) {
        if (userRepository.existsByEmail(newEmail) || userRepository.existsByUsername(newEmail)) {
            throw new IllegalArgumentException("Email is already in use");
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
            @CacheEvict(cacheNames = CacheNames.EDIT_PROFILE_BY_EMAIL, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.EDIT_PROFILE_BY_ID, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.EDIT_PROFILE_ALL, allEntries = true)
    })
    public void confirm(Long userId, String otp) {
        String key = KEY_PREFIX + userId;
        List<Object> pending = redis.opsForHash().multiGet(key, List.of("email", "otpHash"));
        if (pending.get(0) == null) {
            throw new IllegalArgumentException("No pending email change, or it expired");
        }
        // counted before the check, so parallel guesses cannot exceed the limit
        if (redis.opsForHash().increment(key, "attempts", 1) > MAX_ATTEMPTS) {
            redis.delete(key);
            throw new IllegalArgumentException("Too many wrong codes; start the email change again");
        }
        if (!encoder.matches(otp, (String) pending.get(1))) {
            throw new IllegalArgumentException("Invalid code");
        }
        redis.delete(key);

        String newEmail = (String) pending.get(0);
        if (userRepository.existsByEmail(newEmail) || userRepository.existsByUsername(newEmail)) {
            throw new IllegalArgumentException("Email is already in use");
        }
        User user = userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
        editProfileRepository.findByEmail(user.getEmail()).ifPresent(profile -> profile.setEmail(newEmail));
        user.setEmail(newEmail);
        sessionService.revokeAll(userId);
        log.info("Email changed for user {}", userId);
    }
}
