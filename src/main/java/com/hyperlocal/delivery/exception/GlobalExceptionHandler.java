package com.hyperlocal.delivery.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.hyperlocal.delivery.dto.common.ApiError;
import com.hyperlocal.delivery.util.ResponseBuilder;
import com.hyperlocal.delivery.util.TimeUtils;

/**
 * Central HTTP translation layer. Converts typed domain exceptions and
 * common framework exceptions into the canonical {@link ApiError}
 * envelope with the correct HTTP status.
 *
 * <p>Registered with {@link Ordered#HIGHEST_PRECEDENCE} so that our
 * handlers run before any default Spring advice.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handle OTP verification failures. Returns structured OTP error response
     * with the failure reason and optional remaining attempts.
     */
    @ExceptionHandler(OtpVerificationException.class)
    public ResponseEntity<com.hyperlocal.delivery.dto.auth.OtpErrorResponse> handleOtpVerification(
            OtpVerificationException ex, HttpServletRequest request) {
        ErrorCode code = ex.getCode();
        var body = new com.hyperlocal.delivery.dto.auth.OtpErrorResponse(
                ex.getMessage(),
                code.name(),
                ex.getAttemptsRemaining());
        return ResponseEntity.status(code.getStatus()).body(body);
    }

    /**
     * Handle every typed domain exception. The HTTP status and code are
     * derived from the attached {@link ErrorCode}.
     */
    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiError> handleDomain(DomainException ex, HttpServletRequest request) {
        ErrorCode code = ex.getCode();
        ApiError body = ResponseBuilder.error(code, ex.getMessage(), null, request.getRequestURI());
        return ResponseEntity.status(code.getStatus()).body(body);
    }

    /**
     * {@code @Valid}/{@code @RequestBody} validation failure. Returns the
     * first field error so clients can highlight the offending input.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        FieldError first = ex.getBindingResult().getFieldErrors().stream().findFirst().orElse(null);
        String field = first != null ? first.getField() : null;
        String message = first != null && first.getDefaultMessage() != null
                ? first.getDefaultMessage()
                : "Validation failed";
        ApiError body = ResponseBuilder.error(
                ErrorCode.VALIDATION_ERROR, message, field, request.getRequestURI());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * {@code @Validated} method-level validation failure (typically on
     * {@code @RequestParam} / {@code @PathVariable}).
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {
        ConstraintViolation<?> first = ex.getConstraintViolations().stream().findFirst().orElse(null);
        String field = first != null ? first.getPropertyPath().toString() : null;
        String message = first != null ? first.getMessage() : "Validation failed";
        ApiError body = ResponseBuilder.error(
                ErrorCode.VALIDATION_ERROR, message, field, request.getRequestURI());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * Body deserialization failed (malformed JSON, unknown enum value,
     * type mismatch inside the body, etc.).
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleMessageNotReadable(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        ApiError body = ResponseBuilder.error(
                ErrorCode.VALIDATION_ERROR, "Malformed JSON request", null, request.getRequestURI());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * Path/query parameter could not be converted to the target type
     * (e.g. non-numeric id where {@code Long} is expected).
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        String requiredType = ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "value";
        String message = "Parameter '" + ex.getName() + "' must be a valid " + requiredType;
        ApiError body = ResponseBuilder.error(
                ErrorCode.VALIDATION_ERROR, message, ex.getName(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * Unknown URL — no controller matched the request path.
     */
    @ExceptionHandler({ NoHandlerFoundException.class, NoResourceFoundException.class })
    public ResponseEntity<ApiError> handleNotFound(Exception ex, HttpServletRequest request) {
        String path = request.getRequestURI();
        ApiError body = ApiError.of(
                "NOT_FOUND",
                "Endpoint not found: " + path,
                null,
                TimeUtils.nowIso(),
                path);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    /**
     * Spring Security authentication failure that propagates out of the
     * filter chain (unusual once {@code JwtAuthenticationEntryPoint} is
     * wired, but handled defensively).
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthentication(
            AuthenticationException ex, HttpServletRequest request) {
        ApiError body = ResponseBuilder.error(
                ErrorCode.UNAUTHORIZED,
                ex.getMessage() != null ? ex.getMessage() : "Authentication required",
                null,
                request.getRequestURI());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }

    /**
     * Spring Security authorisation failure (missing role or SpEL
     * {@code @PreAuthorize} check failed).
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(
            AccessDeniedException ex, HttpServletRequest request) {
        ApiError body = ResponseBuilder.error(
                ErrorCode.FORBIDDEN,
                ex.getMessage() != null ? ex.getMessage() : "Access denied",
                null,
                request.getRequestURI());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    /**
     * Wrong HTTP method for an existing endpoint (e.g. DELETE on a
     * POST-only route). Returns 405 Method Not Allowed instead of
     * falling through to the 500 catch-all.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        String message = "Method '" + ex.getMethod() + "' is not supported for this endpoint";
        ApiError body = ApiError.of(
                "METHOD_NOT_ALLOWED",
                message,
                null,
                TimeUtils.nowIso(),
                request.getRequestURI());
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(body);
    }

    /**
     * Last-resort fallback. Logs the full stack trace but returns a
     * sanitised message so internal details never leak to the client.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleFallback(Exception ex, HttpServletRequest request) {
        String reference = ApiError.generateReference();
        log.error("Unhandled exception while processing {} [{}]", request.getRequestURI(), reference, ex);
        ApiError body = ApiError.of(
                ErrorCode.INTERNAL_ERROR.name(),
                "An unexpected error occurred",
                null,
                TimeUtils.nowIso(),
                request.getRequestURI(),
                reference);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
