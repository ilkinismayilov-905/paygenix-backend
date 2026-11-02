package com.example.paygenix.security;

import com.example.paygenix.exceptions.GlobalHandlerException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;

// 401 and 403 raised by the security filters, in the same Problem Details shape as GlobalHandlerException.
@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final JsonMapper jsonMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        write(response, HttpStatus.UNAUTHORIZED, "Full authentication is required to access this resource", "UNAUTHORIZED");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        write(response, HttpStatus.FORBIDDEN, "Access denied", "FORBIDDEN");
    }

    // T-100: the filters run before the controller advice, so outages there are answered here in the same shape
    public void serviceUnavailable(HttpServletResponse response) throws IOException {
        response.setHeader(HttpHeaders.RETRY_AFTER, "30");
        write(response, HttpStatus.SERVICE_UNAVAILABLE, "Service temporarily unavailable; try again later", "SERVICE_UNAVAILABLE");
    }

    private void write(HttpServletResponse response, HttpStatus status, String detail, String code) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        jsonMapper.writeValue(response.getOutputStream(), GlobalHandlerException.problem(status, detail, code));
    }
}
