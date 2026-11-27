package com.example.paygenix.observability;

import com.example.paygenix.support.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

// M9: health and Prometheus metrics on the management port only; the API port does not serve them.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "management.server.port=0")
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ManagementEndpointsTest {

    @LocalServerPort int apiPort;
    @LocalManagementPort int managementPort;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void readinessIsUpWithDatabaseAndRedis() throws Exception {
        HttpResponse<String> res = get(managementPort, "/actuator/health/readiness");
        assertThat(res.statusCode()).isEqualTo(200);
        assertThat(res.body()).contains("\"status\":\"UP\"").contains("\"db\"").contains("\"redis\"");
        assertThat(get(managementPort, "/actuator/health/liveness").statusCode()).isEqualTo(200);
    }

    @Test
    void prometheusNeedsNoTokenOnTheManagementPort() throws Exception {
        get(apiPort, "/api/v1/legal/terms"); // one API request, so the HTTP histogram has a series
        HttpResponse<String> res = get(managementPort, "/actuator/prometheus");
        assertThat(res.statusCode()).isEqualTo(200);
        assertThat(res.body())
                .contains("http_server_requests_seconds_bucket")
                .contains("application=\"paygenix\"")
                .contains("jvm_memory_used_bytes");
    }

    @Test
    void apiPortDoesNotServeActuator() throws Exception {
        for (String path : new String[]{"/actuator/prometheus", "/actuator/health", "/actuator/env"}) {
            HttpResponse<String> res = get(apiPort, path);
            assertThat(res.statusCode()).as(path).isIn(401, 404);
            assertThat(res.body()).as(path).doesNotContain("jvm_").doesNotContain("\"status\":\"UP\"");
        }
    }

    @Test
    void onlyHealthInfoAndPrometheusAreExposed() throws Exception {
        // not exposed, so not an endpoint: the API's chain answers 401 before the 404
        for (String path : new String[]{"/actuator/env", "/actuator/heapdump", "/actuator/configprops"}) {
            HttpResponse<String> res = get(managementPort, path);
            assertThat(res.statusCode()).as(path).isIn(401, 404);
            assertThat(res.body()).as(path).doesNotContain("propertySources").doesNotContain("JWT_SECRET");
        }
    }

    private HttpResponse<String> get(int port, String path) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
