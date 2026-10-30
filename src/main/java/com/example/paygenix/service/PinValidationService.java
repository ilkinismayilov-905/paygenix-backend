package com.example.paygenix.service;

import com.example.paygenix.entity.User;
import com.example.paygenix.exceptions.NotFoundException;
import com.example.paygenix.exceptions.PinRejectedException;
import com.example.paygenix.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;

/**
 * Checks the transaction PIN with an attempt limit (FLOW-7): after 5 wrong PINs, PIN checks are locked
 * for 15 minutes from the last wrong one. The user row is locked for the caller's transaction, so one
 * user's checks run one at a time; the counter lives in Redis so a wrong attempt survives the rollback
 * of the payment or transfer it belonged to.
 */
@Service
@RequiredArgsConstructor
public class PinValidationService {

    static final int MAX_ATTEMPTS = 5;
    static final Duration LOCK = Duration.ofMinutes(15);
    private static final String KEY_PREFIX = "pin:failures:";
    // INCR and PEXPIRE in one step: a counter must never be left without its expiry (a permanent lock)
    private static final RedisScript<Long> RECORD_FAILURE = RedisScript.of(
            "local n = redis.call('INCR', KEYS[1]); redis.call('PEXPIRE', KEYS[1], ARGV[1]); return n", Long.class);

    private final UserRepository userRepository;
    private final PasswordEncoder pinEncoder;
    private final StringRedisTemplate redis;

    @Transactional(propagation = Propagation.MANDATORY)
    public void validatePin(Long userId, String rawPin) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        String key = KEY_PREFIX + userId;
        String failures = redis.opsForValue().get(key);
        if (failures != null && failureCount(failures) >= MAX_ATTEMPTS) {
            throw PinRejectedException.locked();
        }

        if (!pinEncoder.matches(rawPin, user.getPinHash())) {
            Long now = redis.execute(RECORD_FAILURE, List.of(key), String.valueOf(LOCK.toMillis()));
            throw now != null && now >= MAX_ATTEMPTS ? PinRejectedException.locked() : PinRejectedException.invalid();
        }
        if (failures != null) {
            resetFailures(userId);
        }
    }

    // After the PIN is changed through the e-mail OTP flow the user is no longer locked out.
    public void resetFailures(Long userId) {
        redis.delete(KEY_PREFIX + userId);
    }

    // A value we cannot read counts as locked: fail closed.
    private static long failureCount(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return MAX_ATTEMPTS;
        }
    }
}
