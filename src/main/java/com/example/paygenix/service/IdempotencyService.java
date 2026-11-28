package com.example.paygenix.service;

import com.example.paygenix.entity.User;
import com.example.paygenix.exceptions.ApiException;
import com.example.paygenix.exceptions.IdempotencyKeyReusedException;
import com.example.paygenix.repository.IdempotencyKeyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * Runs a non-repeatable action (money movement, card issue) at most once per (user, Idempotency-Key). The key is claimed in the same
 * transaction as the action, so a failed action leaves no key behind and can be retried.
 * Keys are kept for 24 hours (Housekeeping purges older ones): a retry is expected within minutes, not days.
 */
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private static final int MAX_KEY_LENGTH = 255;
    /** A claim with no response this old belongs to a request that died (executeInSteps); it may be taken over. */
    static final Duration ABANDONED_AFTER = Duration.ofMinutes(5);

    private final IdempotencyKeyRepository repository;
    private final PanService panService;
    private final JsonMapper jsonMapper;
    private final TransactionTemplate transactions;

    /**
     * @param fingerprint what makes two requests "the same" (endpoint + business fields); it is stored only
     *                    as a keyed HMAC because it may contain card numbers. Leave the PIN out of it.
     */
    @Transactional
    public <T> T execute(User user, String key, String fingerprint, Class<T> responseType, Supplier<T> action) {
        return execute(user, key, fingerprint, responseType, action, UnaryOperator.identity());
    }

    /**
     * @param redact applied to the response before it is stored, so a replay returns the redacted form
     *               (e.g. a card number that may be shown only once must not be kept)
     */
    @Transactional
    public <T> T execute(User user, String key, String fingerprint, Class<T> responseType, Supplier<T> action,
                         UnaryOperator<T> redact) {
        checkKey(key);
        String requestHash = panService.hmac("idempotency:" + fingerprint);

        if (repository.claim(user.getId(), key, requestHash) == 0) {
            var existing = repository.findByUserIdAndIdemKey(user.getId(), key).orElseThrow();
            if (!existing.getRequestHash().equals(requestHash)) {
                throw new IdempotencyKeyReusedException();
            }
            return jsonMapper.readValue(existing.getResponseBody(), responseType);
        }

        T response = action.get();
        repository.saveResponse(user.getId(), key, jsonMapper.writeValueAsString(redact.apply(response)));
        return response;
    }

    /**
     * Like {@link #execute}, for an action that commits in several steps and waits on another service in between
     * (a phone payment, T-144), so it must not run inside one long transaction. The key is claimed in a short
     * transaction of its own and the response saved in another; an action that fails gives the key back, so the same
     * key can retry. While the first request still runs, a second one with the key gets 409 RESOURCE_BUSY. A claim
     * left behind by a request that died is taken over after {@link #ABANDONED_AFTER}; the action itself must be
     * safe to run again (the payment flow's own state makes it so).
     */
    public <T> T executeInSteps(User user, String key, String fingerprint, Class<T> responseType, Supplier<T> action) {
        checkKey(key);
        String requestHash = panService.hmac("idempotency:" + fingerprint);
        Optional<T> replay = transactions.execute(tx -> {
            if (repository.claim(user.getId(), key, requestHash) == 1) {
                return Optional.<T>empty();
            }
            var existing = repository.findByUserIdAndIdemKey(user.getId(), key).orElseThrow();
            if (!existing.getRequestHash().equals(requestHash)) {
                throw new IdempotencyKeyReusedException();
            }
            if (!existing.getResponseBody().isEmpty()) {
                return Optional.of(jsonMapper.readValue(existing.getResponseBody(), responseType));
            }
            if (existing.getCreatedAt().isBefore(Instant.now().minus(ABANDONED_AFTER))
                    && repository.takeOver(user.getId(), key, existing.getCreatedAt()) == 1) {
                return Optional.<T>empty();
            }
            throw new ApiException(HttpStatus.CONFLICT, "RESOURCE_BUSY", "This request is still running; retry shortly");
        });
        if (replay.isPresent()) {
            return replay.get();
        }
        T response;
        try {
            response = action.get();
        } catch (RuntimeException e) {
            transactions.executeWithoutResult(tx -> repository.release(user.getId(), key));
            throw e;
        }
        transactions.executeWithoutResult(tx ->
                repository.saveResponse(user.getId(), key, jsonMapper.writeValueAsString(response)));
        return response;
    }

    private static void checkKey(String key) {
        if (key == null || key.isBlank() || key.length() > MAX_KEY_LENGTH) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_IDEMPOTENCY_KEY",
                    "Idempotency-Key header must be 1-" + MAX_KEY_LENGTH + " characters");
        }
    }
}
