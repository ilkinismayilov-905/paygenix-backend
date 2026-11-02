package com.example.paygenix.service;

import com.example.paygenix.dto.request.LoginRequest;
import com.example.paygenix.dto.response.JwtTokenResponse;
import com.example.paygenix.entity.User;
import com.example.paygenix.exceptions.InvalidOAuthCodeException;
import com.example.paygenix.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final LoginAttemptService loginAttemptService;
    private final SessionService sessionService;
    private final OAuthCodeService oAuthCodeService;
    private final UserRepository userRepository;

    public JwtTokenResponse login(LoginRequest loginRequest, String deviceName) {
        loginAttemptService.countAttempt(loginRequest.email());
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.email(), loginRequest.password())
        );
        loginAttemptService.clear(loginRequest.email());

        User user = (User) authentication.getPrincipal();
        JwtTokenResponse tokens = sessionService.start(user, deviceName);
        log.info("User {} logged in", user.getId());
        return tokens;
    }

    public JwtTokenResponse loginWithOAuthCode(String code, String deviceName) {
        User user = userRepository.findById(oAuthCodeService.redeem(code))
                .orElseThrow(InvalidOAuthCodeException::new);
        return sessionService.start(user, deviceName);
    }
}
