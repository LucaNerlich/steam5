package org.steam5.web;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingRequestHeaderException;
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
        // The value is raw client input and Tomcat decodes %0A/%0D, so logging it would
        // let a caller forge log lines. The parameter name and expected type are enough.
        log.warn("Type mismatch on parameter {} (expected {})", ex.getName(), ex.getRequiredType());
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

    // Spring MVC's own client-error exceptions already carry their HTTP status:
    // HttpMediaTypeNotSupportedException for a text/plain body (415),
    // HttpMediaTypeNotAcceptableException for Accept: application/xml (406) and
    // MissingRequestHeaderException for an absent required header (400). They must be
    // mapped here, because the Exception.class handler below matches first and turned
    // every one of them into a 500 with an error-level stack trace — reporting the
    // caller's own malformed request as an internal error and spamming the logs.
    //
    // `ErrorResponse` cannot be the annotation value (it is an interface, and
    // @ExceptionHandler takes throwable types), so the concrete types are listed. Spring
    // picks the most specific handler, so the tailored handlers above keep their messages.
    @ExceptionHandler({
            HttpMediaTypeNotSupportedException.class,
            HttpMediaTypeNotAcceptableException.class,
            MissingRequestHeaderException.class
    })
    public ResponseEntity<ApiError> handleMvcClientError(Exception ex, HttpServletRequest request) {
        final HttpStatus status = ex instanceof HttpMediaTypeNotSupportedException ? HttpStatus.UNSUPPORTED_MEDIA_TYPE
                : ex instanceof HttpMediaTypeNotAcceptableException ? HttpStatus.NOT_ACCEPTABLE
                : HttpStatus.BAD_REQUEST;
        log.warn("Rejected request: status={} uri={}", status.value(), request.getRequestURI());
        return ResponseEntity.status(status).body(
            ApiError.of(status.value(), status.getReasonPhrase(), request.getRequestURI())
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


