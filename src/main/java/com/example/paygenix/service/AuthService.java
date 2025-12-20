package com.example.paygenix.service;

import com.example.paygenix.converter.UserConverter;
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
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    @Value("${REFRESH_EXPIRATION}")
    private Long refreshExpiration;

    @Value("${EXPIRATION}")
    private Long jwtExpiration;

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserConverter converter;

    public JwtTokenResponse login(LoginRequest loginRequest) {
    Authentication authentication = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(loginRequest.email(), loginRequest.password())
    );

    User user = (User) authentication.getPrincipal();
    User user1 = userRepository.findById(user.getId())
            .orElseThrow(() -> new NotFoundException("User not found"));
    RefreshToken exRefreshToken = refreshTokenRepository.findByUserId(user1.getId());

    String token = jwtService.generateJwtToken(user, user.getId(), user.getUsername());
    String refreshToken = jwtService.generateJwtRefreshToken(user);

        log.info("User logged in successfully: {}", user.getUsername());
    RefreshToken entityRefreshToken = new RefreshToken();
        entityRefreshToken.setTokenHash(refreshToken);
        entityRefreshToken.setUser(user1);
        entityRefreshToken.setExpiresIn(refreshExpiration);

        if(exRefreshToken != null){
        refreshTokenRepository.delete(exRefreshToken);
    }


    RefreshToken savedRefreshToken = refreshTokenRepository.save(entityRefreshToken);

        return JwtTokenResponse.builder()
                .token(token)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtExpiration)
                .user(converter.mapToUserInfo(user))
            .build();

}


    public JwtTokenResponse refreshToken(String refreshToken){

        try {

            log.info("Refresh token process started");
            if (refreshToken == null || refreshToken.trim().isEmpty()) {
                throw new RuntimeException("Refresh token boşdur!");
            }

            if (jwtService.isTokenExpired(refreshToken)){
                throw new RuntimeException("Refresh token is not validated!");
            }

            RefreshToken entityRefreshToken = refreshTokenRepository.findByTokenHash(refreshToken)
                    .orElseThrow(() -> new NotFoundException("Refresh token is not found"));

            if(entityRefreshToken.getCreatedAt().plusSeconds(entityRefreshToken.getExpiresIn())
                    .isBefore(LocalDateTime.now())){
                refreshTokenRepository.delete(entityRefreshToken);
                throw new RuntimeException("Refresh token is expired");
            }

            User user = entityRefreshToken.getUser();

            if(user == null || !user.isEnabled()){
                throw new NotFoundException("User not found");
            }

            String newToken = jwtService.generateJwtToken(user, user.getId(), user.getUsername());

            String newRefreshToken = jwtService.generateJwtRefreshToken(user);

            refreshTokenRepository.delete(entityRefreshToken);

            RefreshToken newEntityRefreshToken = new RefreshToken();
            newEntityRefreshToken.setTokenHash(newRefreshToken);
            newEntityRefreshToken.setUser(user);
            newEntityRefreshToken.setExpiresIn(86400L);

            refreshTokenRepository.save(newEntityRefreshToken);

            log.info("Token refresh edildi: {}", user.getEmail());

            return JwtTokenResponse.builder()
                    .token(newToken)
                    .refreshToken(newRefreshToken)
                    .tokenType("Bearer")
                    .expiresIn(604800L)
                    .user(converter.mapToUserInfo(user))
                    .build();
        } catch (Exception e) {
            log.error("Token refresh zamanı xəta: {}", e.getMessage());
            throw new RuntimeException("Token refresh edilə bilmədi: " + e.getMessage());
        }
    }
}
