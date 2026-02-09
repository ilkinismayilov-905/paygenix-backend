package com.example.paygenix.exceptions;

import com.example.paygenix.dto.response.ErrorResponse;
import com.example.paygenix.dto.response.VerificationErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalHandlerException {
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Object> userNotFound(NotFoundException ex, HttpServletRequest request) {

        String path = request.getRequestURI();
        if (path.contains("/v3/api-docs") || path.contains("/swagger-ui")) {
            return ResponseEntity.ok().build();
        }

        Map<String, Object> error = new LinkedHashMap<>();
        error.put("timestamp", LocalDateTime.now());
        error.put("status", HttpStatus.NOT_FOUND.value());
        error.put("error", "Not Found");
        error.put("message", ex.getMessage());
        error.put("path", request.getRequestURI());

        return new ResponseEntity<>(error, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(InsufficientBalanceException.class)
    public ResponseEntity<Map<String, Object>> insufficientBalance(InsufficientBalanceException ex, HttpServletRequest request) {

        String path = request.getRequestURI();
        if (path.contains("/v3/api-docs") || path.contains("/swagger-ui")) {
            return ResponseEntity.ok().build();
        }

        Map<String, Object> error = new LinkedHashMap<>();
        error.put("timestamp", LocalDateTime.now());
        error.put("status", HttpStatus.CONFLICT.value());
        error.put("message", ex.getMessage());
        error.put("path", request.getRequestURI());

        return new ResponseEntity<>(error, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(VerificationFailedException.class)
    public ResponseEntity<VerificationErrorResponse> handleVerificationFailed(
            VerificationFailedException ex
    ) {
        return ResponseEntity
                .badRequest()
                .body(new VerificationErrorResponse(
                        "Verification failed",
                        ex.getMismatchedFields()
                ));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(new ErrorResponse(OffsetDateTime.now(), HttpStatus.BAD_REQUEST.value(), ex.getMessage(), "BAD_REQUEST"));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntime(RuntimeException ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(
                        OffsetDateTime.now(),
                        HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        "Internal server error",
                        "INTERNAL_SERVER_ERROR"
                ));
    }


    @ExceptionHandler(BadCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorResponse handleBadCredentials(BadCredentialsException ex) {
        return new ErrorResponse(
                OffsetDateTime.now(), 401, "Email or password is incorrect", "BAD_CREDENTIALS");
    }
}
