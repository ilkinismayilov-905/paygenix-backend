package com.example.paygenix.service;

import com.example.paygenix.exceptions.InvalidOAuthCodeException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

/**
 * One-time code handed to the frontend after Google sign-in (SEC-9): the redirect URL carries this code, never a
 * token. The frontend trades it once, within a minute, for a session via POST /api/auth/oauth/exchange. Redis holds
 * only the code's hash and the user id.
 */
@Service
@RequiredArgsConstructor
public class OAuthCodeService {

    static final Duration TTL = Duration.ofSeconds(60);
    private static final String KEY_PREFIX = "oauth:code:";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redis;

    public String issue(Long userId) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String code = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        redis.opsForValue().set(KEY_PREFIX + SessionService.sha256(code), userId.toString(), TTL);
        return code;
    }

    public Long redeem(String code) {
        String userId = redis.opsForValue().getAndDelete(KEY_PREFIX + SessionService.sha256(code));
        if (userId == null) {
            throw new InvalidOAuthCodeException();
        }
        return Long.valueOf(userId);
    }
}
