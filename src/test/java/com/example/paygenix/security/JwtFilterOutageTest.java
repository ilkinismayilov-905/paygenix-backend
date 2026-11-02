package com.example.paygenix.security;

import com.example.paygenix.service.CustomUserDetailsService;
import com.example.paygenix.service.JwtService;
import com.example.paygenix.service.SessionService;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.transaction.CannotCreateTransactionException;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// T-100: a DB/Redis outage while checking the token is a 503, not a 401 that makes clients log out.
class JwtFilterOutageTest {

    private final JwtService jwtService = mock(JwtService.class);
    private final CustomUserDetailsService userDetailsService = mock(CustomUserDetailsService.class);
    private final JwtFilter filter = new JwtFilter(jwtService, userDetailsService, mock(SessionService.class),
            new JwtAuthenticationEntryPoint(JsonMapper.builder().build()));

    @Test
    void outageDuringAuthenticationIsServiceUnavailable() throws Exception {
        when(jwtService.extractTokenUse(anyString())).thenReturn(JwtService.ACCESS);
        when(jwtService.extractUsername(anyString())).thenReturn("a@example.test");

        for (RuntimeException outage : new RuntimeException[]{
                new CannotCreateTransactionException("Could not open JPA EntityManager"),
                new DataAccessResourceFailureException("Connection refused")}) {
            doThrow(outage).when(userDetailsService).loadUserByUsername(anyString());
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();

            filter.doFilter(request(), response, chain);

            assertThat(response.getStatus()).isEqualTo(503);
            assertThat(response.getHeader("Retry-After")).isEqualTo("30");
            assertThat(response.getContentAsString()).contains("\"code\":\"SERVICE_UNAVAILABLE\"");
            assertThat(chain.getRequest()).as("request must not reach the controller").isNull();
        }
    }

    @Test
    void badTokenStillFallsThroughUnauthenticated() throws Exception {
        when(jwtService.extractTokenUse(anyString())).thenThrow(new IllegalArgumentException("malformed"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request(), response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    private static MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/user/me");
        request.addHeader("Authorization", "Bearer token");
        return request;
    }
}
