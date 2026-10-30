package com.example.paygenix.exceptions;

import com.example.paygenix.dto.response.ErrorResponse;
import jakarta.persistence.LockTimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.OffsetDateTime;
import java.util.stream.Collectors;


@RestControllerAdvice
@Slf4j
public class GlobalHandlerException {

    // 1. Ümumi cavab hazırlayan köməkçi metod (Kod təkrarını azaldır)
    private ResponseEntity<ErrorResponse> buildError(HttpStatus status, String message, String errorCode) {
        ErrorResponse error = new ErrorResponse(
                OffsetDateTime.now(),
                status.value(),
                message,
                errorCode
        );
        return ResponseEntity.status(status).body(error);
    }

    // 2. Tapılmadı xətaları (NotFoundException)
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException ex) {
        return buildError(HttpStatus.NOT_FOUND, ex.getMessage(), "NOT_FOUND");
    }

    // Row lock not acquired within lock_timeout: raw from EntityManager calls, translated from repositories.
    @ExceptionHandler({LockTimeoutException.class, PessimisticLockingFailureException.class})
    public ResponseEntity<ErrorResponse> handleLockTimeout(Exception ex) {
        log.warn("Lock wait timed out: {}", ex.getMessage());
        return buildError(HttpStatus.CONFLICT, "Resource is busy, retry shortly", "RESOURCE_BUSY");
    }

    // 3. Balans çatışmazlığı (InsufficientBalanceException)
    @ExceptionHandler(InsufficientBalanceException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientBalance(InsufficientBalanceException ex) {
        return buildError(HttpStatus.CONFLICT, ex.getMessage(), "INSUFFICIENT_BALANCE");
    }

    // 4. Doğrulama xətası (VerificationFailedException)
    // Qeyd: Əgər VerificationErrorResponse xüsusi sahələrə malikdirsə onu saxlaya bilərsən,
    // lakin vahidlik üçün mesaj hissəsinə sahələri də əlavə etdim.
    @ExceptionHandler(VerificationFailedException.class)
    public ResponseEntity<ErrorResponse> handleVerificationFailed(VerificationFailedException ex) {
        String details = "Mismatched fields: " + ex.getMismatchedFields().stream()
                .map(Object::toString)
                .collect(Collectors.joining(", "));
        return buildError(HttpStatus.BAD_REQUEST, details, "VERIFICATION_FAILED");
    }

    // 5. Yanlış arqumentlər (IllegalArgumentException)
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException ex) {
        return buildError(HttpStatus.BAD_REQUEST, ex.getMessage(), "BAD_REQUEST");
    }

    // 6. Təhlükəsizlik xətaları (BadCredentialsException)
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException ex) {
        return buildError(HttpStatus.UNAUTHORIZED, "Email or password is incorrect", "UNAUTHORIZED");
    }

    // Field names and messages only: rejected values may be card numbers or PINs.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .sorted()
                .collect(Collectors.joining("; "));
        return buildError(HttpStatus.BAD_REQUEST, message, "VALIDATION_FAILED");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex) {
        return buildError(HttpStatus.BAD_REQUEST, "Malformed request body", "MALFORMED_REQUEST");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        return buildError(HttpStatus.FORBIDDEN, "Access denied", "FORBIDDEN");
    }

    // 7. Biznes xətaları (BusinessException)
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException ex) {
        HttpStatus status = resolveStatus(ex);
        return buildError(status, ex.getMessage(), ex.getErrorCode());
    }

    // 8. Giriş edilməyib (UserNotLoginException)
    @ExceptionHandler({
            UserNotLoginException.class,
            NoResourceFoundException.class
    })
    public ResponseEntity<ErrorResponse> handleNotLoginUser(UserNotLoginException ex) {
        log.warn("Unauthorized access attempt: {}", ex.getMessage());
        return buildError(HttpStatus.UNAUTHORIZED, ex.getMessage(), "USER_NOT_LOGGED_IN");
    }

    // 9. Gözlənilməyən sistem xətaları (RuntimeException / Exception)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneralException(Exception ex) {
        ex.printStackTrace();
        log.error("Unhandled exception: ", ex);
        return buildError(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", "INTERNAL_SERVER_ERROR");
    }

    // Köməkçi status həll edici
    private HttpStatus resolveStatus(BusinessException ex) {
        if (ex instanceof InvalidReportCategoryException || ex instanceof InvalidReportMessageException) {
            return HttpStatus.BAD_REQUEST;
        }
        if (ex instanceof TooManyReportsException) {
            return HttpStatus.TOO_MANY_REQUESTS;
        }
        if (ex instanceof TransferRejectedException) {
            return HttpStatus.BAD_REQUEST;
        }
        if (ex instanceof CardHasBalanceException || ex instanceof PaymentAlreadyProcessedException) {
            return HttpStatus.CONFLICT;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }
}