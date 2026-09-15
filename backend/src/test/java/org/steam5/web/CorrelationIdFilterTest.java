package org.steam5.web;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    /** Captures the correlationId visible in MDC while the filter chain runs. */
    private String runFilterAndCaptureMdc(String suppliedHeader) throws Exception {
        final MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/review-game/today");
        if (suppliedHeader != null) {
            req.addHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, suppliedHeader);
        }
        final MockHttpServletResponse resp = new MockHttpServletResponse();
        final String[] seenDuringChain = new String[1];
        final FilterChain chain = (r, s) -> seenDuringChain[0] = MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY);
        filter.doFilterInternal(req, resp, chain);
        return seenDuringChain[0];
    }

    @AfterEach
    void clearMdc() {
        MDC.remove(CorrelationIdFilter.CORRELATION_ID_MDC_KEY);
    }

    @Test
    void aSafeClientSuppliedCorrelationIdIsPreserved() throws Exception {
        final String seen = runFilterAndCaptureMdc("client-request-42");
        assertEquals("client-request-42", seen);
    }

    @Test
    void aCrlfInjectionAttemptIsReplacedWithAGeneratedId() throws Exception {
        final String malicious = "abc\r\n2026-01-01 WARN forged log line";
        final String seen = runFilterAndCaptureMdc(malicious);
        assertFalse(seen.contains("\r"), "the raw header value must never reach MDC/logs unsanitized");
        assertFalse(seen.contains("\n"));
        assertTrue(seen.matches("^[A-Za-z0-9_-]{1,64}$"), "a replacement id must be generated instead");
    }

    @Test
    void anOverlongHeaderIsReplacedWithAGeneratedId() throws Exception {
        final String seen = runFilterAndCaptureMdc("x".repeat(200));
        assertTrue(seen.matches("^[A-Za-z0-9_-]{1,64}$"));
    }

    @Test
    void aMissingHeaderGetsAGeneratedId() throws Exception {
        final String seen = runFilterAndCaptureMdc(null);
        assertTrue(seen.matches("^[A-Za-z0-9_-]{1,8}$"));
    }

    @Test
    void mdcIsClearedAfterTheRequestCompletes() throws Exception {
        runFilterAndCaptureMdc("client-request-42");
        assertEquals(null, MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY));
    }
}
