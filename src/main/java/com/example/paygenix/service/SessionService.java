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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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

    private final UserSessionRepository sessions;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final UserMapper mapper;

    @Transactional
    public JwtTokenResponse start(User user, String deviceName) {
        UserSession session = new UserSession();
        session.setId(UUID.randomUUID());
        session.setUserId(user.getId());
        session.setDeviceName(deviceName == null || deviceName.length() <= DEVICE_NAME_MAX
                ? deviceName : deviceName.substring(0, DEVICE_NAME_MAX));
        session.setCreatedAt(Instant.now());
        return issueTokens(session, user);
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

        UserSession session = sessions.findByIdForUpdate(sessionId)
                .filter(s -> s.isActive(Instant.now()))
                .orElseThrow(InvalidRefreshTokenException::invalid);
        if (!MessageDigest.isEqual(session.getRefreshTokenHash().getBytes(StandardCharsets.US_ASCII),
                sha256(refreshToken).getBytes(StandardCharsets.US_ASCII))) {
            session.setRevokedAt(Instant.now());
            log.warn("Refresh token reuse detected, session {} of user {} revoked", session.getId(), session.getUserId());
            throw InvalidRefreshTokenException.reused();
        }
        User user = userRepository.findById(session.getUserId())
                .filter(User::isEnabled)
                .orElseThrow(InvalidRefreshTokenException::invalid);
        return issueTokens(session, user);
    }

    @Transactional(readOnly = true)
    public boolean isActive(UUID sessionId, Long userId) {
        return sessions.existsByIdAndUserIdAndRevokedAtIsNullAndExpiresAtAfter(sessionId, userId, Instant.now());
    }

    @Transactional(readOnly = true)
    public List<SessionResponse> list(Long userId, UUID currentSessionId) {
        return sessions.findByUserIdAndRevokedAtIsNullAndExpiresAtAfterOrderByLastUsedAtDesc(userId, Instant.now())
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

    private JwtTokenResponse issueTokens(UserSession session, User user) {
        Instant now = Instant.now();
        String token = jwtService.generateJwtToken(user, user.getId(), user.getUsername(), session.getId());
        String refreshToken = jwtService.generateJwtRefreshToken(user, session.getId());
        session.setRefreshTokenHash(sha256(refreshToken));
        session.setLastUsedAt(now);
        // shortcut: sliding expiry, a session refreshed regularly never ends; add an absolute cap if needed (T-078)
        session.setExpiresAt(now.plusSeconds(refreshExpiration));
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
