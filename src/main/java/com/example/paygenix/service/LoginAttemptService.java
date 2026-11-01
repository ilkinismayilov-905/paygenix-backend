package com.example.paygenix.service;

import com.example.paygenix.exceptions.LoginLockedException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

/**
 * Login attempt limit (SEC-8): at most 5 attempts per email in a 15-minute window that starts at the
 * first attempt; a successful login clears it. The attempt is counted before the password is checked,
 * so parallel guesses cannot all slip past the limit. Unknown emails are counted the same way, so the
 * lock does not reveal whether an account exists. The window is fixed, not sliding, so guessing inside a
 * window does not extend it.
 * shortcut: anyone who knows an email can lock it again every 15 minutes (accepted DoS); add per-IP limits
 * once trusted-proxy config exists (T-076). Without Redis login fails closed with 500 (see T-064).
 */
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    static final int MAX_ATTEMPTS = 5;
    static final Duration WINDOW = Duration.ofMinutes(15);
    private static final String KEY_PREFIX = "login:attempts:";
    // INCR and EXPIRE in one step: a key left without a TTL would lock the account forever.
    // TTL == -1 (not n == 1) also repairs a key that lost its TTL earlier. Shared with EmailChangeService.
    static final RedisScript<Long> COUNT_ATTEMPT = RedisScript.of("""
            local n = redis.call('INCR', KEYS[1])
            if redis.call('TTL', KEYS[1]) == -1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end
            return n
            """, Long.class);

    private final StringRedisTemplate redis;

    public void countAttempt(String email) {
        Long attempts = redis.execute(COUNT_ATTEMPT, List.of(key(email)), String.valueOf(WINDOW.toSeconds()));
        if (attempts == null || attempts > MAX_ATTEMPTS) {
            throw new LoginLockedException(WINDOW);
        }
    }

    public void clear(String email) {
        redis.delete(key(email));
    }

    private static String key(String email) {
        return KEY_PREFIX + email.trim().toLowerCase(Locale.ROOT);
    }
}
