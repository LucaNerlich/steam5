package org.steam5.web;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/details/abc");

    @Test
    void typeMismatch_returns400() {
        final MethodArgumentTypeMismatchException ex = new MethodArgumentTypeMismatchException(
                "notanumber", Long.class, "appId", null, null);
        final ResponseEntity<ApiError> res = handler.handleTypeMismatch(ex, request);
        assertEquals(400, res.getStatusCode().value());
    }

    @Test
    void missingParameter_returns400() {
        final MissingServletRequestParameterException ex = new MissingServletRequestParameterException("month", "String");
        final ResponseEntity<ApiError> res = handler.handleMissingParameter(ex, request);
        assertEquals(400, res.getStatusCode().value());
    }

    @Test
    void unreadableBody_returns400() {
        final HttpMessageNotReadableException ex = new HttpMessageNotReadableException("bad json", (org.springframework.http.HttpInputMessage) null);
        final ResponseEntity<ApiError> res = handler.handleUnreadableBody(ex, request);
        assertEquals(400, res.getStatusCode().value());
    }

    @Test
    void methodNotSupported_returns405() {
        final HttpRequestMethodNotSupportedException ex = new HttpRequestMethodNotSupportedException("POST");
        final ResponseEntity<ApiError> res = handler.handleMethodNotSupported(ex, request);
        assertEquals(405, res.getStatusCode().value());
    }
}
