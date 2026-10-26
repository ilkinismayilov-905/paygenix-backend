package com.example.paygenix.security;

import com.example.paygenix.dto.request.LoginRequest;
import com.example.paygenix.dto.response.JwtTokenResponse;
import com.example.paygenix.entity.User;
import com.example.paygenix.service.AuthService;
import com.example.paygenix.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class LoginTest extends IntegrationTest {

    @Autowired AuthService authService;

    @Test
    void loginReturnsTokens() {
        User user = createUser();

        JwtTokenResponse response = authService.login(new LoginRequest(user.getEmail(), PIN));

        assertThat(response.token()).isNotBlank();
        assertThat(response.refreshToken()).isNotBlank();
    }
}
