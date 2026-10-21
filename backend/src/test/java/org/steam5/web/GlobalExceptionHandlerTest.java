package org.steam5.web;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
    void typeMismatch_doesNotLogTheRawValue() {
        final Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        final ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            final MethodArgumentTypeMismatchException ex = new MethodArgumentTypeMismatchException(
                    "x\n2026-10-20 ERROR forged", Long.class, "limit", null, null);
            handler.handleTypeMismatch(ex, request);
            assertFalse(appender.list.stream()
                    .anyMatch(event -> event.getFormattedMessage().contains("forged")));
            assertTrue(appender.list.stream()
                    .anyMatch(event -> event.getFormattedMessage().contains("limit")));
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void missingParameter_returns400() {
        final MissingServletRequestParameterException ex = new MissingServletRequestParameterException("month", "String");
        final ResponseEntity<ApiError> res = handler.handleMissingParameter(ex, request);
        assertEquals(400, res.getStatusCode().value());
    }

    @Test
    void unreadableBody_doesNotLogTheClientValue() {
        final ch.qos.logback.classic.Logger logger =
                (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class);
        final ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
                new ch.qos.logback.core.read.ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            final HttpMessageNotReadableException ex = new HttpMessageNotReadableException(
                    "JSON parse error: x\n2026-10-21 ERROR forged", (org.springframework.http.HttpInputMessage) null);
            handler.handleUnreadableBody(ex, request);
            org.junit.jupiter.api.Assertions.assertEquals(1, appender.list.size());
            final ch.qos.logback.classic.spi.ILoggingEvent event = appender.list.getFirst();
            org.junit.jupiter.api.Assertions.assertFalse(event.getFormattedMessage().contains("forged"));
            org.junit.jupiter.api.Assertions.assertTrue(event.getThrowableProxy() == null
                    || !event.getThrowableProxy().getMessage().contains("forged"));
        } finally {
            logger.detachAppender(appender);
        }
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

    // --- Exceptions with their own status must not be reported as 500 ---
    // These go through the real MVC dispatch pipeline, because the defect was about which
    // @ExceptionHandler Spring picks (the Exception.class one used to win).

    @Test
    void unsupportedContentType_isRoutedTo415() throws Exception {
        mockMvc().perform(post("/api/probe")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("{}")
                        .header("X-Probe-Token", "t"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void unacceptableAcceptHeader_isRoutedTo406() throws Exception {
        mockMvc().perform(post("/api/probe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .header("X-Probe-Token", "t")
                        .accept(MediaType.APPLICATION_XML))
                .andExpect(status().isNotAcceptable());
    }

    @Test
    void missingRequiredHeader_isRoutedTo400() throws Exception {
        mockMvc().perform(post("/api/probe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    private static MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(new ProbeController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    /** Minimal endpoint whose exceptions come from Spring MVC's own binding, not from the code. */
    @RestController
    static class ProbeController {

        @PostMapping(value = "/api/probe", consumes = MediaType.APPLICATION_JSON_VALUE,
                produces = MediaType.APPLICATION_JSON_VALUE)
        Map<String, String> probe(@RequestBody(required = false) Map<String, String> body,
                                  @RequestHeader("X-Probe-Token") String token) {
            return Map.of("ok", "true");
        }
    }
}
