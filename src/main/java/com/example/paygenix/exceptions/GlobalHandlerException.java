package com.example.paygenix.exceptions;

import jakarta.persistence.LockTimeoutException;
import jakarta.persistence.PessimisticLockException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.lang.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.stream.Collectors;

/**
 * Every error is an RFC 9457 Problem Details body (application/problem+json) with a stable `code` extension that
 * clients branch on. `detail` is written for humans and never carries exception internals; unexpected errors are
 * logged and answered with a generic 500. Spring MVC's own exceptions (405, 415, 404 for unknown paths, ...) come
 * from the base class and get their HTTP status name as `code`.
 */
@RestControllerAdvice
@Slf4j
public class GlobalHandlerException extends ResponseEntityExceptionHandler {

    public static final String CODE = "code";

    public static ProblemDetail problem(HttpStatusCode status, String detail, String code) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty(CODE, code);
        return problem;
    }

    private static ResponseEntity<ProblemDetail> error(HttpStatus status, String detail, String code) {
        return ResponseEntity.status(status).body(problem(status, detail, code));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ProblemDetail> handleBusiness(BusinessException ex) {
        return error(ex.getStatus(), ex.getMessage(), ex.getErrorCode());
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(NotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage(), "NOT_FOUND");
    }

    // Row lock not acquired within lock_timeout: raw from EntityManager calls, translated from repositories.
    @ExceptionHandler({LockTimeoutException.class, PessimisticLockException.class, PessimisticLockingFailureException.class})
    public ResponseEntity<ProblemDetail> handleLockTimeout(Exception ex) {
        log.warn("Lock wait timed out: {}", ex.getMessage());
        return error(HttpStatus.CONFLICT, "Resource is busy, retry shortly", "RESOURCE_BUSY");
    }

    @ExceptionHandler(InsufficientBalanceException.class)
    public ResponseEntity<ProblemDetail> handleInsufficientBalance(InsufficientBalanceException ex) {
        return error(HttpStatus.CONFLICT, ex.getMessage(), "INSUFFICIENT_BALANCE");
    }

    @ExceptionHandler(VerificationFailedException.class)
    public ResponseEntity<ProblemDetail> handleVerificationFailed(VerificationFailedException ex) {
        String details = "Mismatched fields: " + ex.getMismatchedFields().stream()
                .map(Object::toString)
                .collect(Collectors.joining(", "));
        return error(HttpStatus.BAD_REQUEST, details, "VERIFICATION_FAILED");
    }

    // Our own services throw it with a client-safe message for bad input.
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleBadRequest(IllegalArgumentException ex) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage(), "BAD_REQUEST");
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ProblemDetail> handleBadCredentials(BadCredentialsException ex) {
        return error(HttpStatus.UNAUTHORIZED, "Email or password is incorrect", "UNAUTHORIZED");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        return error(HttpStatus.FORBIDDEN, "Access denied", "FORBIDDEN");
    }

    @ExceptionHandler(UserNotLoginException.class)
    public ResponseEntity<ProblemDetail> handleNotLoggedIn(UserNotLoginException ex) {
        return error(HttpStatus.UNAUTHORIZED, ex.getMessage(), "USER_NOT_LOGGED_IN");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", "INTERNAL_SERVER_ERROR");
    }

    // Field names and messages only: rejected values may be card numbers or PINs.
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .sorted()
                .collect(Collectors.joining("; "));
        return handleExceptionInternal(ex, problem(status, detail, "VALIDATION_FAILED"), headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex, HttpHeaders headers,
                                                        HttpStatusCode status, WebRequest request) {
        return handleExceptionInternal(ex, problem(status, ex.getPropertyName() + ": invalid value", "VALIDATION_FAILED"),
                headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        return handleExceptionInternal(ex, problem(status, "Malformed request body", "MALFORMED_REQUEST"),
                headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleServletRequestBindingException(ServletRequestBindingException ex, HttpHeaders headers,
                                                                          HttpStatusCode status, WebRequest request) {
        ProblemDetail body = ex instanceof MissingRequestHeaderException missing
                ? problem(status, "Required header " + missing.getHeaderName() + " is missing", "MISSING_HEADER")
                : null;
        return handleExceptionInternal(ex, body, headers, status, request);
    }

    // Gives Spring's own Problem Details (405, 415, 404 ...) a `code` too.
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, @Nullable Object body, HttpHeaders headers,
                                                             HttpStatusCode status, WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, status, request);
        if (response != null && response.getBody() instanceof ProblemDetail problem
                && (problem.getProperties() == null || !problem.getProperties().containsKey(CODE))) {
            HttpStatus resolved = HttpStatus.resolve(status.value());
            problem.setProperty(CODE, resolved != null ? resolved.name() : "ERROR");
        }
        return response;
    }
}
