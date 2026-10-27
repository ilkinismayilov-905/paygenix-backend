package com.example.paygenix.service;


import com.example.paygenix.cache.CacheNames;
import com.example.paygenix.mapper.UserMapper;
import com.example.paygenix.dto.request.LoginRequest;
import com.example.paygenix.dto.response.JwtTokenResponse;
import com.example.paygenix.entity.RefreshToken;
import com.example.paygenix.entity.User;
import com.example.paygenix.exceptions.NotFoundException;
import com.example.paygenix.repository.RefreshTokenRepository;
import com.example.paygenix.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    @Value("${jwt.refresh-expiration}")
    private Long refreshExpiration;

    @Value("${jwt.expiration}")
    private Long jwtExpiration;

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final EmailService emailService;

    private final UserMapper mapper;

    public JwtTokenResponse login(LoginRequest loginRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.email(), loginRequest.password())
        );

        User user = (User) authentication.getPrincipal();
        User user1 = userRepository.findById(user.getId())
                .orElseThrow(() -> new NotFoundException("User not found"));
        RefreshToken exRefreshToken = getRefreshTokenByUserIdCached(user1.getId());

        String token = jwtService.generateJwtToken(user, user.getId(), user.getUsername());
        String refreshToken = jwtService.generateJwtRefreshToken(user);

        log.info("User logged in successfully: {}", user.getUsername());
        RefreshToken entityRefreshToken = new RefreshToken();
        entityRefreshToken.setTokenHash(refreshToken);
        entityRefreshToken.setUser(user1);
        entityRefreshToken.setExpiresIn(refreshExpiration);

        if (exRefreshToken != null) {
            refreshTokenRepository.delete(exRefreshToken);
            evictRefreshTokenCaches(exRefreshToken.getUser().getId(), exRefreshToken.getTokenHash());
        }


        RefreshToken savedRefreshToken = refreshTokenRepository.save(entityRefreshToken);
        putRefreshTokenCaches(savedRefreshToken);

        return JwtTokenResponse.builder()
                .token(token)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtExpiration)
                .user(mapper.userToUserResponse(user))
                .build();

    }


    public JwtTokenResponse refreshToken(String refreshToken) {

        try {

            log.info("Refresh token process started");
            if (refreshToken == null || refreshToken.trim().isEmpty()) {
                throw new RuntimeException("Refresh token boşdur!");
            }

            if (!JwtService.REFRESH.equals(jwtService.extractTokenUse(refreshToken))) {
                throw new RuntimeException("Not a refresh token");
            }

            if (jwtService.isTokenExpired(refreshToken)) {
                throw new RuntimeException("Refresh token is not validated!");
            }

            RefreshToken entityRefreshToken = getRefreshTokenByTokenHashCached(refreshToken)
                    .orElseThrow(() -> new NotFoundException("Refresh token is not found"));

            if (entityRefreshToken.getCreatedAt().plusSeconds(entityRefreshToken.getExpiresIn())
                    .isBefore(LocalDateTime.now())) {
                refreshTokenRepository.delete(entityRefreshToken);
                evictRefreshTokenCaches(entityRefreshToken.getUser().getId(), entityRefreshToken.getTokenHash());
                throw new RuntimeException("Refresh token is expired");
            }

            User user = entityRefreshToken.getUser();

            if (user == null || !user.isEnabled()) {
                throw new NotFoundException("User not found");
            }

            String newToken = jwtService.generateJwtToken(user, user.getId(), user.getUsername());
            String newRefreshToken = jwtService.generateJwtRefreshToken(user);

            refreshTokenRepository.delete(entityRefreshToken);
            evictRefreshTokenCaches(user.getId(), entityRefreshToken.getTokenHash());


            RefreshToken newEntityRefreshToken = new RefreshToken();
            newEntityRefreshToken.setTokenHash(newRefreshToken);
            newEntityRefreshToken.setUser(user);
            newEntityRefreshToken.setExpiresIn(86400L);

            RefreshToken savedNewToken = refreshTokenRepository.save(newEntityRefreshToken);
            putRefreshTokenCaches(savedNewToken);

            log.info("Token refresh edildi: {}", user.getEmail());

            return JwtTokenResponse.builder()
                    .token(newToken)
                    .refreshToken(newRefreshToken)
                    .tokenType("Bearer")
                    .expiresIn(604800L)
                    .user(mapper.userToUserResponse(user))
                    .build();
        } catch (Exception e) {
            log.error("Token refresh zamanı xəta: {}", e.getMessage());
            throw new RuntimeException("Token refresh edilə bilmədi: " + e.getMessage());
        }
    }

    @Cacheable(cacheNames = CacheNames.REFRESH_TOKEN_BY_USER_ID, key = "#userId")
    public RefreshToken getRefreshTokenByUserIdCached(Long userId) {
        return refreshTokenRepository.findByUserId(userId);
    }

    @Cacheable(cacheNames = CacheNames.REFRESH_TOKEN_BY_TOKEN_HASH, key = "#tokenHash")
    public Optional<RefreshToken> getRefreshTokenByTokenHashCached(String tokenHash) {
        return refreshTokenRepository.findByTokenHash(tokenHash);
    }

    @Caching(put = {
            @CachePut(cacheNames = CacheNames.REFRESH_TOKEN_BY_USER_ID, key = "#refreshToken.user.id"),
            @CachePut(cacheNames = CacheNames.REFRESH_TOKEN_BY_TOKEN_HASH, key = "#refreshToken.tokenHash")
    })
    public RefreshToken putRefreshTokenCaches(RefreshToken refreshToken) {
        return refreshToken;
    }

    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.REFRESH_TOKEN_BY_USER_ID, key = "#userId"),
            @CacheEvict(cacheNames = CacheNames.REFRESH_TOKEN_BY_TOKEN_HASH, key = "#tokenHash")
    })
    public void evictRefreshTokenCaches(Long userId, String tokenHash) {
    }

}
