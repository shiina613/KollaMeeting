package com.example.kolla.exceptions;

import com.example.kolla.responses.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.validation.ConstraintViolationException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Global exception handler — converts exceptions to structured ErrorResponse.
 * Requirements: 15.1–15.7
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssxxx");

    // ── Custom application exceptions ────────────────────────────────────────

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(
            ResourceNotFoundException ex, HttpServletRequest request) {
        log.debug("Resource not found: {}", ex.getMessage());
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request, null);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(
            BadRequestException ex, HttpServletRequest request) {
        log.debug("Bad request: {}", ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request, null);
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(
            ForbiddenException ex, HttpServletRequest request) {
        log.debug("Forbidden: {}", ex.getMessage());
        return build(HttpStatus.FORBIDDEN, ex.getMessage(), request, null);
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(
            UnauthorizedException ex, HttpServletRequest request) {
        log.debug("Unauthorized: {}", ex.getMessage());
        return build(HttpStatus.UNAUTHORIZED, ex.getMessage(), request, null);
    }

    @ExceptionHandler(SchedulingConflictException.class)
    public ResponseEntity<ErrorResponse> handleSchedulingConflict(
            SchedulingConflictException ex, HttpServletRequest request) {
        log.debug("Scheduling conflict: {}", ex.getMessage());
        Map<String, Object> details = new HashMap<>();
        details.put("conflictingMeetingId", ex.getConflictingMeetingId());
        return build(HttpStatus.CONFLICT, ex.getMessage(), request, details);
    }

    // ── Spring Security exceptions ───────────────────────────────────────────

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            AccessDeniedException ex, HttpServletRequest request) {
        log.debug("Access denied: {}", ex.getMessage());
        return build(HttpStatus.FORBIDDEN, "Access denied", request, null);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(
            AuthenticationException ex, HttpServletRequest request) {
        log.debug("Authentication failed: {}", ex.getMessage());
        return build(HttpStatus.UNAUTHORIZED, "Authentication required", request, null);
    }

    // ── Validation exceptions ────────────────────────────────────────────────

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        log.debug("Validation failed: {}", fieldErrors);
        return build(HttpStatus.BAD_REQUEST, "Validation failed", request, fieldErrors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {
        Map<String, String> violations = new HashMap<>();
        ex.getConstraintViolations().forEach(cv -> {
            String field = cv.getPropertyPath().toString();
            violations.put(field, cv.getMessage());
        });
        log.debug("Constraint violation: {}", violations);
        return build(HttpStatus.BAD_REQUEST, "Constraint violation", request, violations);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMessageNotReadable(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        log.debug("Message not readable: {}", ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, "Malformed or unreadable request body", request, null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        log.debug("Method not supported: {}", ex.getMessage());
        return build(HttpStatus.METHOD_NOT_ALLOWED,
                "HTTP method '" + ex.getMethod() + "' is not supported for this endpoint",
                request, null);
    }

    // ── Catch-all ────────────────────────────────────────────────────────────

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(
            Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception at {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred. Please try again later.",
                request, null);
    }

    // ── Builder helper ───────────────────────────────────────────────────────

    private ResponseEntity<ErrorResponse> build(HttpStatus status,
                                                 String message,
                                                 HttpServletRequest request,
                                                 Object details) {
        ErrorResponse body = ErrorResponse.builder()
                .timestamp(ZonedDateTime.now(ZONE).format(FORMATTER))
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .path(request.getRequestURI())
                .details(details)
                .build();
        return ResponseEntity.status(status).body(body);
    }
}
