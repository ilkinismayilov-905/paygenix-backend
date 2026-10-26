package com.example.paygenix.support;

import com.example.paygenix.entity.User;
import com.example.paygenix.enums.Role;
import com.example.paygenix.repository.UserRepository;
import com.example.paygenix.service.JwtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;
import java.util.UUID;

/** Base for integration tests: real Postgres and Redis in containers, MockMvc, and user/token helpers. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTest {

    public static final String PIN = "123456";

    @Autowired protected MockMvc mockMvc;
    @Autowired protected UserRepository userRepository;
    @Autowired protected PasswordEncoder passwordEncoder;
    @Autowired protected JwtService jwtService;

    protected User createUser(Role... roles) {
        String id = UUID.randomUUID().toString().substring(0, 8);
        return userRepository.save(User.builder()
                .email("user-" + id + "@example.test")
                .username("user" + id)
                .fullname("Test User " + id)
                .pinHash(passwordEncoder.encode(PIN))
                .roles(Set.of(roles.length == 0 ? new Role[]{Role.USER_ROLE} : roles))
                .status(User.Status.ACTIVE)
                .build());
    }

    protected String bearer(User user) {
        return "Bearer " + jwtService.generateJwtToken(user, user.getId(), user.getUsername());
    }
}
