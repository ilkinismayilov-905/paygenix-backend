package com.example.paygenix.service;

import com.example.paygenix.dto.response.JwtTokenResponse;
import com.example.paygenix.dto.response.SessionResponse;
import com.example.paygenix.entity.User;
import com.example.paygenix.entity.UserSession;
import com.example.paygenix.exceptions.InvalidRefreshTokenException;
import com.example.paygenix.exceptions.NotFoundException;
import com.example.paygenix.mapper.UserMapper;
import com.example.paygenix.repository.UserRepository;
import com.example.paygenix.repository.UserSessionRepository;
import io.jsonwebtoken.JwtException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.LockedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Device sessions (decision #13, SEC-18). Each login starts a session; its refresh token is rotated on every
 * refresh and only its SHA-256 is stored. A refresh token that is validly signed for a session but is not the
 * current one was already rotated, so someone replayed it: the whole session is revoked. Access tokens carry
 * the session id and stop working as soon as the session is revoked.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SessionService {

    // Set by JwtFilter so controllers know which session the request came from.
    public static final String SESSION_ID_ATTRIBUTE = "paygenix.sessionId";
    private static final int DEVICE_NAME_MAX = 255;

    @Value("${jwt.expiration}")
    private Long jwtExpiration;

    @Value("${jwt.refresh-expiration}")
    private Long refreshExpiration;

    // Absolute cap from sign-in: refreshing slides expires_at, but never past createdAt + this.
    @Value("${jwt.session-max-lifetime}")
    private Long sessionMaxLifetime;

    @PostConstruct
    void checkConfig() {
        if (sessionMaxLifetime <= 0) {
            throw new IllegalStateException("jwt.session-max-lifetime must be positive");
        }
    }

    // Ended rows are kept this long for incident review, then purged.
    static final Duration ENDED_SESSION_RETENTION = Duration.ofDays(30);

    private final UserSessionRepository sessions;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final UserMapper mapper;

    @Transactional
    public JwtTokenResponse start(User user, String deviceName) {
        if (!user.isAccountNonLocked()) { // every sign-in path (password, Google) ends here
            throw new LockedException("Account is locked");
        }
        UserSession session = new UserSession();
        session.setId(UUID.randomUUID());
        session.setUserId(user.getId());
        session.setDeviceName(deviceName == null || deviceName.length() <= DEVICE_NAME_MAX
                ? deviceName : deviceName.substring(0, DEVICE_NAME_MAX));
        session.setCreatedAt(Instant.now());
        return issueTokens(session, user, session.getCreatedAt());
    }

    // noRollbackFor: revoking a session on reuse must be committed even though the request fails
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public JwtTokenResponse refresh(String refreshToken) {
        UUID sessionId;
        try {
            if (!JwtService.REFRESH.equals(jwtService.extractTokenUse(refreshToken))) {
                throw InvalidRefreshTokenException.invalid();
            }
            sessionId = jwtService.extractSessionId(refreshToken);
        } catch (JwtException | IllegalArgumentException e) {
            throw InvalidRefreshTokenException.invalid();
        }
        if (sessionId == null) {
            throw InvalidRefreshTokenException.invalid();
        }

        Instant now = Instant.now();
        // second check: the cap may have been lowered in config after expires_at was set
        UserSession session = sessions.findByIdForUpdate(sessionId)
                .filter(s -> s.isActive(now) && s.getCreatedAt().plusSeconds(sessionMaxLifetime).isAfter(now))
                .orElseThrow(InvalidRefreshTokenException::invalid);
        if (!MessageDigest.isEqual(session.getRefreshTokenHash().getBytes(StandardCharsets.US_ASCII),
                sha256(refreshToken).getBytes(StandardCharsets.US_ASCII))) {
            session.setRevokedAt(Instant.now());
            log.warn("Refresh token reuse detected, session {} of user {} revoked", session.getId(), session.getUserId());
            throw InvalidRefreshTokenException.reused();
        }
        User user = userRepository.findById(session.getUserId())
                .filter(u -> u.isEnabled() && u.isAccountNonLocked())
                .orElseThrow(InvalidRefreshTokenException::invalid);
        return issueTokens(session, user, now);
    }

    @Transactional(readOnly = true)
    public boolean isActive(UUID sessionId, Long userId) {
        Instant now = Instant.now();
        return sessions.existsByIdAndUserIdAndRevokedAtIsNullAndExpiresAtAfterAndCreatedAtAfter(
                sessionId, userId, now, now.minusSeconds(sessionMaxLifetime));
    }

    @Transactional(readOnly = true)
    public List<SessionResponse> list(Long userId, UUID currentSessionId) {
        Instant now = Instant.now();
        return sessions.findByUserIdAndRevokedAtIsNullAndExpiresAtAfterAndCreatedAtAfterOrderByLastUsedAtDesc(
                        userId, now, now.minusSeconds(sessionMaxLifetime))
                .stream()
                .map(s -> new SessionResponse(s.getId(), s.getDeviceName(), s.getCreatedAt(), s.getLastUsedAt(),
                        s.getId().equals(currentSessionId)))
                .toList();
    }

    @Transactional
    public void revoke(Long userId, UUID sessionId) {
        UserSession session = sessions.findByIdAndUserIdAndRevokedAtIsNull(sessionId, userId)
                .orElseThrow(() -> new NotFoundException("Session not found"));
        session.setRevokedAt(Instant.now());
    }

    @Transactional
    public void revokeAll(Long userId) {
        sessions.revokeAll(userId, Instant.now());
    }

    // Daily; idempotent, so running on several instances at once is harmless.
    @Scheduled(cron = "0 30 3 * * *", zone = "UTC")
    @Transactional
    public int purgeEnded() {
        int purged = sessions.deleteEndedBefore(Instant.now().minus(ENDED_SESSION_RETENTION));
        if (purged > 0) {
            log.info("Purged {} ended sessions", purged);
        }
        return purged;
    }

    // now: the instant the caller checked the session against, so the new expires_at cannot already be past
    private JwtTokenResponse issueTokens(UserSession session, User user, Instant now) {
        String token = jwtService.generateJwtToken(user, user.getId(), user.getUsername(), session.getId());
        String refreshToken = jwtService.generateJwtRefreshToken(user, session.getId());
        session.setRefreshTokenHash(sha256(refreshToken));
        session.setLastUsedAt(now);
        Instant sliding = now.plusSeconds(refreshExpiration);
        Instant cap = session.getCreatedAt().plusSeconds(sessionMaxLifetime);
        session.setExpiresAt(sliding.isBefore(cap) ? sliding : cap);
        sessions.save(session);

        return JwtTokenResponse.builder()
                .token(token)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtExpiration)
                .user(mapper.userToUserResponse(user))
                .build();
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
