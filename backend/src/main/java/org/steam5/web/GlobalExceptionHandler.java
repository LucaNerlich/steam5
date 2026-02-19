package org.steam5.web;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ResponseStatusException;
import org.steam5.http.ReviewGameException;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

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
        log.error("Unhandled exception:", ex);
        // NEVER expose ex.getMessage() for generic exceptions - security issue
        return ResponseEntity.status(500).body(
            ApiError.of(500, "An internal error occurred", request.getRequestURI())
        );
    }
}


