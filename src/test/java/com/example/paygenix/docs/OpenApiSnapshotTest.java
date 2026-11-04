package com.example.paygenix.docs;

import com.example.paygenix.support.IntegrationTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * The frontend generates its API types from frontend/openapi.json. This fails when the backend contract
 * changed and the snapshot did not. Refresh it with: UPDATE_OPENAPI=true ./mvnw test -Dtest=OpenApiSnapshotTest
 */
@TestPropertySource(properties = "springdoc.api-docs.enabled=true")
class OpenApiSnapshotTest extends IntegrationTest {

    private static final Path SNAPSHOT = Path.of("frontend/openapi.json");
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void frontendSnapshotMatchesLiveSpec() throws Exception {
        String live = mockMvc.perform(get("/v3/api-docs")).andReturn().getResponse().getContentAsString();
        JsonNode liveTree = JSON.readTree(live);
        if ("true".equals(System.getenv("UPDATE_OPENAPI"))) {
            Files.writeString(SNAPSHOT, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(liveTree) + "\n");
        }
        assertThat(JSON.readTree(Files.readString(SNAPSHOT)))
                .as("frontend/openapi.json is stale; see the class comment to refresh it")
                .isEqualTo(liveTree);
    }
}
