package com.example.paygenix.service;

import com.example.paygenix.exceptions.LoginLockedException;
import com.example.paygenix.exceptions.LoginRateLimitedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.Inet6Address;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/**
 * Login attempt limits, both counted before the password is checked, so parallel guesses cannot all slip past.
 * <ul>
 * <li>Per email (SEC-8): at most 5 attempts in a 15-minute window that starts at the first attempt; a successful
 * login clears it. Unknown emails are counted the same way, so the lock does not reveal whether an account exists.
 * The window is fixed, not sliding, so guessing inside a window does not extend it.</li>
 * <li>Per client address (T-142): at most {@code paygenix.login.ip-max-attempts} (30) attempts in 15 minutes across
 * all emails, which stops one machine from trying a password against many accounts (credential stuffing). The address
 * is the real client behind a trusted proxy (T-076); IPv6 counts per /64, the block one subscriber usually gets. A
 * successful login gives its attempt back, so many people behind one NAT are not locked out by their own sign-ins.</li>
 * </ul>
 * The address limit is checked first, so a blocked address no longer adds to any email's count.
 * shortcut: anyone who knows an email can lock it again every 15 minutes (accepted DoS); a botnet with many addresses
 * is only slowed by the per-email limit. Without Redis login fails closed with 500.
 */
@Service
public class LoginAttemptService {

    static final int MAX_ATTEMPTS = 5;
    static final Duration WINDOW = Duration.ofMinutes(15);
    private static final String KEY_PREFIX = "login:attempts:";
    private static final String IP_KEY_PREFIX = "login:ip:";
    // INCR and EXPIRE in one step: a key left without a TTL would lock the account forever.
    // TTL == -1 (not n == 1) also repairs a key that lost its TTL earlier. Shared with EmailChangeService.
    static final RedisScript<Long> COUNT_ATTEMPT = RedisScript.of("""
            local n = redis.call('INCR', KEYS[1])
            if redis.call('TTL', KEYS[1]) == -1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end
            return n
            """, Long.class);
    // gives one attempt back, only while the window is still open (never creates a key or goes below zero)
    private static final RedisScript<Long> REFUND_ATTEMPT = RedisScript.of("""
            if (tonumber(redis.call('GET', KEYS[1]) or '0')) > 0 then return redis.call('DECR', KEYS[1]) end
            return 0
            """, Long.class);

    private final StringRedisTemplate redis;
    private final int maxAttemptsPerIp;

    public LoginAttemptService(StringRedisTemplate redis,
                               @Value("${paygenix.login.ip-max-attempts:30}") int maxAttemptsPerIp) {
        this.redis = redis;
        this.maxAttemptsPerIp = maxAttemptsPerIp;
    }

    /** Counts one attempt for the client address and for the email; throws when either is over its limit. */
    public void countAttempt(String email, String clientIp) {
        String ipKey = ipKey(clientIp);
        Long fromIp = redis.execute(COUNT_ATTEMPT, List.of(ipKey), String.valueOf(WINDOW.toSeconds()));
        if (fromIp == null || fromIp > maxAttemptsPerIp) {
            Long ttl = redis.getExpire(ipKey);
            throw new LoginRateLimitedException(ttl != null && ttl > 0 ? Duration.ofSeconds(ttl) : WINDOW);
        }
        Long attempts = redis.execute(COUNT_ATTEMPT, List.of(key(email)), String.valueOf(WINDOW.toSeconds()));
        if (attempts == null || attempts > MAX_ATTEMPTS) {
            throw new LoginLockedException(WINDOW);
        }
    }

    /** After a successful login: the email's count is cleared and the address gets its attempt back. */
    public void succeeded(String email, String clientIp) {
        redis.delete(key(email));
        redis.execute(REFUND_ATTEMPT, List.of(ipKey(clientIp)));
    }

    private static String key(String email) {
        return KEY_PREFIX + email.trim().toLowerCase(Locale.ROOT);
    }

    static String ipKey(String clientIp) {
        return IP_KEY_PREFIX + clientKey(clientIp);
    }

    // IPv4 as is; IPv6 by its /64 prefix (a host can pick any address inside its /64). Anything that is not an IP
    // literal is counted under its own text.
    static String clientKey(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) {
            return "unknown";
        }
        if (!clientIp.contains(":")) {
            return clientIp;
        }
        String literal = clientIp.replaceFirst("%.*$", ""); // drop a zone id (fe80::1%eth0)
        if (!literal.matches("[0-9A-Fa-f:.]+")) {
            return clientIp; // not a literal: never let InetAddress resolve it through DNS
        }
        try {
            InetAddress address = InetAddress.getByName(literal);
            if (address instanceof Inet6Address) {
                byte[] prefix = Arrays.copyOf(address.getAddress(), 8);
                return HexFormat.of().formatHex(prefix) + "/64";
            }
            return address.getHostAddress(); // an IPv4-mapped address comes back as IPv4
        } catch (UnknownHostException e) {
            return clientIp;
        }
    }
}
