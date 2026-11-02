package com.example.paygenix.security;

import com.example.paygenix.exceptions.GlobalHandlerException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// T-064: Redis or the database down (e.g. during the PIN check) is a retryable 503, not a 500.
class BackendUnavailableTest {

    @RestController
    static class ThrowingController {
        @GetMapping("/redis-down")
        String redisDown() {
            throw new RedisConnectionFailureException("Unable to connect to Redis");
        }

        @GetMapping("/redis-timeout")
        String redisTimeout() {
            throw new QueryTimeoutException("Redis command timed out");
        }

        @GetMapping("/db-down")
        String dbDown() {
            throw new CannotCreateTransactionException("Could not open JPA EntityManager for transaction");
        }
    }

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
            .setControllerAdvice(new GlobalHandlerException()).build();

    @Test
    void redisOutageIsServiceUnavailable() throws Exception {
        for (String path : new String[]{"/redis-down", "/redis-timeout", "/db-down"}) {
            mockMvc.perform(get(path))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(header().string("Retry-After", "30"))
                    .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"))
                    .andExpect(jsonPath("$.detail").value("Service temporarily unavailable; try again later"));
        }
    }
}
