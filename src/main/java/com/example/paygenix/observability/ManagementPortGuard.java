package com.example.paygenix.observability;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Metrics and health details are served only on the management port, which compose does not publish. On the API
 * port they would be reachable by anyone who can reach the API, so a configuration that merges the two (or turns the
 * management server off with -1) refuses to start. 0 picks a random free port (tests) and is always separate.
 */
@Component
public class ManagementPortGuard {

    public ManagementPortGuard(@Value("${server.port}") int serverPort,
                               @Value("${management.server.port}") int managementPort) {
        if (managementPort < 0 || (managementPort != 0 && managementPort == serverPort)) {
            throw new IllegalStateException("MANAGEMENT_PORT must be a separate, unpublished port (got "
                    + managementPort + ", API port " + serverPort + ")");
        }
    }
}
