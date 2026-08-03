package org.steam5.web;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.server.ResponseStatusException;
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


