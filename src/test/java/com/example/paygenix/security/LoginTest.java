package com.example.paygenix.security;

import com.example.paygenix.dto.request.LoginRequest;
import com.example.paygenix.dto.response.JwtTokenResponse;
import com.example.paygenix.entity.User;
import com.example.paygenix.service.AuthService;
import com.example.paygenix.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
class LoginTest extends IntegrationTest {

    @Autowired AuthService authService;

    @Test
    void loginReturnsTokens() {
        User user = createUser();

        JwtTokenResponse response = authService.login(new LoginRequest(user.getEmail(), PASSWORD), "test");

        assertThat(response.token()).isNotBlank();
        assertThat(response.refreshToken()).isNotBlank();
    }

    // FLOW-7 / decision #11: the PIN confirms transactions only; it is not a login secret
    @Test
    void pinIsNotAPassword() throws Exception {
        User user = createUser();
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + user.getEmail() + "\",\"password\":\"" + PIN + "\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + user.getEmail() + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk());
    }
}
