package com.example.paygenix.security;

import com.example.paygenix.support.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

// Own property value gives this class its own context, so startup output is captured here.
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(properties = "JWT_SECRET=" + JwtSecretNotLoggedTest.SENTINEL)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class JwtSecretNotLoggedTest {

    static final String SENTINEL = "sentinel-jwt-secret-must-never-appear-in-logs-0123456789";

    @Test
    void startupDoesNotPrintJwtSecret(CapturedOutput output) {
        assertThat(output.getAll()).doesNotContain(SENTINEL);
    }
}
