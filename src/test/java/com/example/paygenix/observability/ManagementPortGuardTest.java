package com.example.paygenix.observability;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ManagementPortGuardTest {

    @Test
    void managementOnTheApiPortRefusesToStart() {
        assertThatThrownBy(() -> new ManagementPortGuard(8000, 8000)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new ManagementPortGuard(8000, -1)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void separateOrRandomPortIsFine() {
        assertThatCode(() -> new ManagementPortGuard(8000, 8081)).doesNotThrowAnyException();
        assertThatCode(() -> new ManagementPortGuard(0, 0)).doesNotThrowAnyException();
    }
}
