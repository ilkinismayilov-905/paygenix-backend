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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Email change (SEC-14): the new address must prove it can receive mail before it replaces the old one, so nobody
 * can claim an address they do not own. The pending change lives in Redis for 10 minutes with at most 5 OTP tries.
 * Codes can be requested once a minute and 5 times a day per user, so restarting cannot be used to keep guessing.
 * The email is the login name and the token subject, so a confirmed change signs out every device.
 * Starting needs the current PIN, and confirming needs a second code sent to the current address (T-106: a token
 * holder can set a first PIN on a PIN-less account, but cannot read the owner's inbox). The old address is also told
 * after the change.
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
    private final PinValidationService pinValidationService; // shares the PIN lockout with payments
    private final TransactionTemplate transactionTemplate;

    public void start(Long userId, String newEmail, String pin) {
        // own short transaction: validatePin locks the user row, which must not stay locked while mail is sent
        transactionTemplate.executeWithoutResult(tx -> pinValidationService.validatePin(userId, pin));
        if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(COOLDOWN_PREFIX + userId, "1", SEND_COOLDOWN))) {
            throw ApiException.otpSendTooFrequent(SEND_COOLDOWN);
        }
        Long startsToday = redis.execute(LoginAttemptService.COUNT_ATTEMPT, List.of(DAILY_PREFIX + userId),
                String.valueOf(Duration.ofDays(1).toSeconds()));
        if (startsToday == null || startsToday > MAX_STARTS_PER_DAY) {
            Long ttl = redis.getExpire(DAILY_PREFIX + userId);
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "EMAIL_CHANGE_LIMIT", "Too many email change requests today",
                    ttl != null && ttl > 0 ? Duration.ofSeconds(ttl) : Duration.ofDays(1));
        }
        // checked after the rate limits, so probing which addresses are registered costs the same as a real change
        if (userRepository.existsByEmail(newEmail) || userRepository.existsByUsername(newEmail)) {
            throw emailTaken();
        }
        String currentEmail = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found")).getEmail();
        String otp = OtpGenerator.newOtp();
        String currentEmailOtp = OtpGenerator.newOtp();
        String key = KEY_PREFIX + userId;
        redis.delete(key);
        redis.opsForHash().putAll(key, Map.of("email", newEmail, "otpHash", encoder.encode(otp),
                "currentOtpHash", encoder.encode(currentEmailOtp), "attempts", "0"));
        redis.expire(key, TTL);
        // the owner's warning first: a send failure on an address the caller picked must not suppress it
        emailService.sendEmailChangeCode(currentEmail, currentEmailOtp);
        emailService.sendOtp(newEmail, otp);
    }

    @Transactional
    @CacheEvict(cacheNames = CacheNames.EDIT_PROFILE_ALL, allEntries = true)
    public void confirm(Long userId, String otp, String currentEmailOtp) {
        String key = KEY_PREFIX + userId;
        // counted before the check, so parallel guesses cannot exceed the limit
        long attempts = redis.opsForHash().increment(key, "attempts", 1);
        List<Object> pending = redis.opsForHash().multiGet(key, List.of("email", "otpHash", "currentOtpHash"));
        if (pending.get(0) == null) {
            redis.delete(key); // the increment recreated an expired key without a TTL
            throw new ApiException(HttpStatus.BAD_REQUEST, "OTP_EXPIRED", "No pending email change, or it expired");
        }
        if (attempts > MAX_ATTEMPTS) {
            redis.delete(key);
            throw ApiException.tooManyAttempts();
        }
        if (pending.get(2) == null) { // started before T-106, has no second code: start again
            redis.delete(key);
            throw new ApiException(HttpStatus.BAD_REQUEST, "OTP_EXPIRED", "No pending email change, or it expired");
        }
        // one error for either code, and | (not ||) checks both, so neither the answer nor its timing tells which was right
        if (!encoder.matches(otp, (String) pending.get(1))
                | !encoder.matches(currentEmailOtp, (String) pending.get(2))) {
            throw ApiException.invalidOtp();
        }
        redis.delete(key);

        String newEmail = User.normalizeEmail((String) pending.get(0)); // pending entries from before T-080 kept the typed case
        if (userRepository.existsByEmail(newEmail) || userRepository.existsByUsername(newEmail)) {
            throw emailTaken();
        }
        User user = userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
        editProfileRepository.findByUserId(userId).ifPresent(profile -> profile.setEmail(newEmail));
        if (user.getProvider() == User.AuthProvider.GOOGLE && user.getPublicUsername().equals(user.getEmail())) {
            user.setUsername(newEmail); // Google accounts use the email as username; keep them in step
        }
        String oldEmail = user.getEmail();
        user.setEmail(newEmail);
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // another account confirmed the same address between the check and the update
            throw emailTaken();
        }
        sessionService.revokeAll(userId);
        log.info("Email changed for user {}", userId);
        // after commit: no row locks or DB connection held during SMTP, and no notice for a change that rolled back
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    emailService.sendEmailChanged(oldEmail);
                } catch (RuntimeException e) {
                    // the change is confirmed by the new address; a mail outage must not undo it.
                    // class name only: mail exception messages can carry the recipient address
                    log.warn("Could not notify the old address of user {} about the email change: {}",
                            userId, e.getClass().getSimpleName());
                }
            }
        });
    }

    private static ApiException emailTaken() {
        return new ApiException(HttpStatus.CONFLICT, "EMAIL_TAKEN", "Email is already taken");
    }
}
