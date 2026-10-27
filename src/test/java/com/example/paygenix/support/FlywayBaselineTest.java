package com.example.paygenix.support;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

// The context only starts if Hibernate (ddl-auto=validate) accepts the schema Flyway built.
class FlywayBaselineTest extends IntegrationTest {

    @Autowired Flyway flyway;

    @Test
    void allMigrationsAreApplied() {
        assertThat(flyway.info().current()).isNotNull();
        assertThat(flyway.info().pending()).isEmpty();
        assertThat(flyway.info().applied()).allMatch(m -> m.getState().isApplied() && !m.getState().isFailed());
    }
}
