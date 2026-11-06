package com.example.paygenix.service;

import com.example.paygenix.entity.User;
import com.example.paygenix.exceptions.ApiException;
import com.example.paygenix.exceptions.IdempotencyKeyReusedException;
import com.example.paygenix.repository.IdempotencyKeyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

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

    private final IdempotencyKeyRepository repository;
    private final PanService panService;
    private final JsonMapper jsonMapper;

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
        if (key == null || key.isBlank() || key.length() > MAX_KEY_LENGTH) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_IDEMPOTENCY_KEY",
                    "Idempotency-Key header must be 1-" + MAX_KEY_LENGTH + " characters");
        }
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
}
