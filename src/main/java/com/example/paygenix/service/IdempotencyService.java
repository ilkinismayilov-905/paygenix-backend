package com.example.paygenix.service;

import com.example.paygenix.entity.User;
import com.example.paygenix.exceptions.IdempotencyKeyReusedException;
import com.example.paygenix.repository.IdempotencyKeyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.function.Supplier;

/**
 * Runs a money-moving action at most once per (user, Idempotency-Key). The key is claimed in the same
 * transaction as the action, so a failed action leaves no key behind and can be retried.
 * shortcut: keys are kept forever; add a purge of rows older than ~24h when the table grows.
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
        if (key == null || key.isBlank() || key.length() > MAX_KEY_LENGTH) {
            throw new IllegalArgumentException("Idempotency-Key header must be 1-" + MAX_KEY_LENGTH + " characters");
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
        repository.saveResponse(user.getId(), key, jsonMapper.writeValueAsString(response));
        return response;
    }
}
