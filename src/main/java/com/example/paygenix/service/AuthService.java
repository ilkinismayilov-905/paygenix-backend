package com.example.paygenix.service;

import com.example.paygenix.dto.request.LoginRequest;
import com.example.paygenix.dto.response.JwtTokenResponse;
import com.example.paygenix.entity.User;
import com.example.paygenix.exceptions.InvalidOAuthCodeException;
import com.example.paygenix.exceptions.LoginLockedException;
import com.example.paygenix.repository.UserRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
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
    private final MeterRegistry meterRegistry;

    public JwtTokenResponse login(LoginRequest loginRequest, String deviceName) {
        Authentication authentication;
        try {
            loginAttemptService.countAttempt(loginRequest.email());
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginRequest.email(), loginRequest.password())
            );
        } catch (LoginLockedException e) {
            countLogin("locked");
            throw e;
        } catch (AuthenticationException e) {
            // a wrong password, or a locked or disabled account (ACCOUNT_LOCKED)
            countLogin(e instanceof BadCredentialsException ? "bad_credentials" : "refused");
            throw e;
        }
        loginAttemptService.clear(loginRequest.email());
        countLogin("success");

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

    // paygenix.auth.logins{outcome}: a rising bad_credentials or locked rate is the signal of a guessing attack
    private void countLogin(String outcome) {
        meterRegistry.counter("paygenix.auth.logins", "outcome", outcome).increment();
    }
}
