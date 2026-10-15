package org.steam5.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AdminTokenFilterTest {

    private static final String TOKEN = "secret-admin-token";

    private final AdminTokenFilter filter = new AdminTokenFilter(TOKEN);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequest request(String method, String uri) {
        final MockHttpServletRequest req = new MockHttpServletRequest(method, uri);
        req.setRequestURI(uri);
        return req;
    }

    private int runFilter(MockHttpServletRequest req, String token) throws ServletException, IOException {
        if (token != null) {
            req.addHeader("X-Admin-Token", token);
        }
        final MockHttpServletResponse resp = new MockHttpServletResponse();
        final FilterChain chain = (r, s) -> {
        };
        filter.doFilterInternal(req, resp, chain);
        return resp.getStatus();
    }

    @Test
    void semicolonPathParametersDoNotBypassTheAdminGate() throws Exception {
        final MockHttpServletRequest req = request("POST", "/api/admin;/seasons/backfill");
        assertFalse(filter.shouldNotFilter(req));
        assertEquals(401, runFilter(req, null));
        assertEquals(200, runFilter(req, TOKEN));
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals("ROLE_ADMIN", SecurityContextHolder.getContext().getAuthentication().getAuthorities().iterator().next().getAuthority());
    }

    @Test
    void metricsAndCachePathsAreGated() {
        assertFalse(filter.shouldNotFilter(request("GET", "/api/metrics/counts")));
        assertFalse(filter.shouldNotFilter(request("GET", "/api/metrics/coverage")));
        assertFalse(filter.shouldNotFilter(request("GET", "/api/cache/stats")));
    }

    @Test
    void classRootEndpointsAreGatedToo() throws Exception {
        // /api/metrics itself is mapped at the controller's class root, so the prefix must
        // match the exact path as well; otherwise the filter never runs, no ROLE_ADMIN is
        // granted, and the request is permanently 401 whatever token is supplied.
        for (final String uri : List.of("/api/metrics", "/api/cache", "/api/admin")) {
            final MockHttpServletRequest req = request("GET", uri);
            assertFalse(filter.shouldNotFilter(req), uri + " must be gated");

            assertEquals(401, runFilter(request("GET", uri), null), uri + " without a token");
            assertEquals(200, runFilter(request("GET", uri), TOKEN), uri + " with the token");
            assertNotNull(SecurityContextHolder.getContext().getAuthentication(), uri);
        }
    }

    @Test
    void publicMetricsSummaryStaysOpen() {
        assertTrue(filter.shouldNotFilter(request("GET", "/api/metrics/picks/summary")));
        assertTrue(filter.shouldNotFilter(request("GET", "/api/review-game/today")));
    }

    @Test
    void publicMetricsSummaryIsNotGated() {
        assertTrue(filter.shouldNotFilter(request("GET", "/api/metrics/picks/summary")));
    }

    @Test
    void publicApiPathsAreNotGated() {
        assertTrue(filter.shouldNotFilter(request("GET", "/api/review-game/today")));
        assertTrue(filter.shouldNotFilter(request("GET", "/api/leaderboard/today")));
    }

    @Test
    void optionsRequestsAreNotGated() {
        assertTrue(filter.shouldNotFilter(request("OPTIONS", "/api/admin/seasons/backfill")));
    }

    @Test
    void wrongTokenIsRejected() throws Exception {
        final MockHttpServletRequest req = request("POST", "/api/admin/seasons/backfill");
        assertEquals(401, runFilter(req, "wrong-token"));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void missingTokenIsRejected() throws Exception {
        assertEquals(401, runFilter(request("GET", "/api/cache/stats"), null));
    }
}
