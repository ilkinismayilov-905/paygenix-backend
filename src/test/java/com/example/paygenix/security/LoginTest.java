package com.example.paygenix.security;

import com.example.paygenix.dto.request.LoginRequest;
import com.example.paygenix.dto.response.JwtTokenResponse;
import com.example.paygenix.entity.User;
import com.example.paygenix.enums.Role;
import com.example.paygenix.repository.UserRepository;
import com.example.paygenix.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class LoginTest {

    @Autowired AuthService authService;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @Test
    void loginReturnsTokens() {
        String email = "login-" + UUID.randomUUID() + "@example.test";
        userRepository.save(User.builder()
                .email(email)
                .username("u" + UUID.randomUUID().toString().substring(0, 8))
                .fullname("Login Test")
                .pinHash(passwordEncoder.encode("123456"))
                .roles(Set.of(Role.USER_ROLE))
                .status(User.Status.ACTIVE)
                .build());

        JwtTokenResponse response = authService.login(new LoginRequest(email, "123456"));

        assertThat(response.token()).isNotBlank();
        assertThat(response.refreshToken()).isNotBlank();
    }
}
