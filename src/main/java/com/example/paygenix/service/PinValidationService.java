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
    // one step, so a lost EXPIRE cannot leave a counter at the limit with no TTL (PIN locked forever);
    // EXPIRE on every failure keeps the window sliding: 5 failures with less than 15 minutes between them
    private static final RedisScript<Long> COUNT_FAILURE = RedisScript.of("""
            local n = redis.call('INCR', KEYS[1])
            redis.call('EXPIRE', KEYS[1], ARGV[1])
            return n
            """, Long.class);

    private final UserRepository userRepository;
    private final PasswordEncoder pinEncoder;
    private final StringRedisTemplate redis;

    @Transactional(propagation = Propagation.MANDATORY)
    public void validatePin(Long userId, String rawPin) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        if (user.getPinHash() == null) {
            throw PinRejectedException.notSet();
        }
        String key = KEY_PREFIX + userId;
        String failures = redis.opsForValue().get(key);
        if (failures != null && Long.parseLong(failures) >= MAX_ATTEMPTS) {
            // later hits are not counted, so the key's TTL is the real time left on the lock
            Long ttl = redis.getExpire(key);
            throw PinRejectedException.locked(ttl != null && ttl > 0 ? Duration.ofSeconds(ttl) : LOCK);
        }

        if (!pinEncoder.matches(rawPin, user.getPinHash())) {
            Long now = redis.execute(COUNT_FAILURE, List.of(key), String.valueOf(LOCK.toSeconds()));
            throw now != null && now >= MAX_ATTEMPTS ? PinRejectedException.locked(LOCK) : PinRejectedException.invalid();
        }
        if (failures != null) {
            redis.delete(key);
        }
    }
}
