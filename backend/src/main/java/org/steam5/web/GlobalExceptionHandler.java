package org.steam5.web;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.steam5.http.ReviewGameException;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    // Thrown when a @Validated controller's @RequestParam/@PathVariable (e.g. @Size) fails.
    // Without this, the generic Exception handler below would catch it first and return 500 —
    // Spring's own default 400 handling for this ErrorResponse-implementing exception only
    // kicks in when no @ExceptionHandler in the app matches, and our Exception.class handler
    // always matches.
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiError> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
                                                                            HttpServletRequest request) {
        log.warn("Request parameter validation failed: uri={}", request.getRequestURI());
        return ResponseEntity.status(400).body(
            ApiError.of(400, "Invalid request parameters", request.getRequestURI())
        );
    }

    @ExceptionHandler(ReviewGameException.class)
    public ResponseEntity<ApiError> handleReviewGameException(ReviewGameException ex,
                                                               HttpServletRequest request) {
        final int status = ex.getStatusCode() > 0 ? ex.getStatusCode() : 500;
        // Log WITH stack trace so root cause is visible in logs
        log.warn("ReviewGameException: status={}", status, ex);
        return ResponseEntity.status(status).body(
            ApiError.of(status, ex.getMessage(), request.getRequestURI()) // This is a controlled message, safe to expose
        );
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> handleResponseStatusException(ResponseStatusException ex,
                                                                   HttpServletRequest request) {
        log.warn("ResponseStatusException: status={}", ex.getStatusCode().value(), ex);
        return ResponseEntity.status(ex.getStatusCode()).body(
            ApiError.of(ex.getStatusCode().value(), ex.getReason(), request.getRequestURI())
        );
    }

    // Client-input binding errors must be 400s logged without stack spam — the
    // generic Exception handler below would turn every one of them into a 500
    // with a full stack trace, letting anonymous callers amplify log volume.

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                       HttpServletRequest request) {
        log.warn("Type mismatch on {}={}: {}", ex.getName(), ex.getValue(), ex.getMessage());
        return ResponseEntity.status(400).body(
            ApiError.of(400, "Invalid request parameter: " + ex.getName(), request.getRequestURI())
        );
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParameter(MissingServletRequestParameterException ex,
                                                           HttpServletRequest request) {
        log.warn("Missing required parameter '{}'", ex.getParameterName());
        return ResponseEntity.status(400).body(
            ApiError.of(400, "Missing required parameter: " + ex.getParameterName(), request.getRequestURI())
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableBody(HttpMessageNotReadableException ex,
                                                         HttpServletRequest request) {
        log.warn("Malformed request body: {}", ex.getMessage());
        return ResponseEntity.status(400).body(
            ApiError.of(400, "Malformed request body", request.getRequestURI())
        );
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex,
                                                             HttpServletRequest request) {
        log.warn("Method {} not supported for {}", ex.getMethod(), request.getRequestURI());
        return ResponseEntity.status(405).body(
            ApiError.of(405, "Method not allowed", request.getRequestURI())
        );
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoResource(NoResourceFoundException ex,
                                                     HttpServletRequest request) {
        log.warn("No resource found: {}", request.getRequestURI());
        return ResponseEntity.status(404).body(
            ApiError.of(404, "Not found", request.getRequestURI())
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGenericException(Exception ex,
                                                             HttpServletRequest request) {
        if (isClientDisconnect(ex)) {
            log.debug("Client disconnected during response write: {} {}", request.getMethod(), request.getRequestURI());
            return ResponseEntity.status(500).body(
                ApiError.of(500, "Client disconnected", request.getRequestURI())
            );
        }
        log.error("Unhandled exception:", ex);
        // NEVER expose ex.getMessage() for generic exceptions - security issue
        return ResponseEntity.status(500).body(
            ApiError.of(500, "An internal error occurred", request.getRequestURI())
        );
    }

    private static boolean isClientDisconnect(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            final String name = current.getClass().getSimpleName();
            if ("ClientAbortException".equals(name)
                    || "AsyncRequestNotUsableException".equals(name)) {
                return true;
            }
            final String msg = current.getMessage();
            if (msg != null && msg.contains("Broken pipe")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}


