package org.steam5.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class RequestPathNormalizerTest {

    @Test
    void stripsSemicolonPathParametersPerSegment() {
        final MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/profile;/76561198000000000");
        req.setRequestURI("/api/profile;/76561198000000000");
        assertEquals("/api/profile/76561198000000000", RequestPathNormalizer.normalizedPath(req));
    }

    @Test
    void stripsContextPath() {
        final MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/profile/1");
        req.setContextPath("/steam5");
        req.setRequestURI("/steam5/api/profile/1");
        assertEquals("/api/profile/1", RequestPathNormalizer.normalizedPath(req));
    }

    @Test
    void decodesPercentEncodedSegments() {
        final MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/users/%73earch");
        req.setRequestURI("/api/users/%73earch");
        assertEquals("/api/users/search", RequestPathNormalizer.normalizedPath(req));
    }

    @Test
    void decodesBeforeStrippingAnEncodedContextPath() {
        final MockHttpServletRequest req = new MockHttpServletRequest("GET", "/steam5/api/users/search");
        req.setContextPath("/steam5");
        req.setRequestURI("/steam%35/api/users/search");
        assertEquals("/api/users/search", RequestPathNormalizer.normalizedPath(req));
    }

    @Test
    void malformedEscapesDoNotThrow() {
        final MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/users/%zz");
        req.setRequestURI("/api/users/%zz");
        assertEquals("/api/users/%zz", RequestPathNormalizer.normalizedPath(req));
    }
}

class ProfileRateLimitFilterTest {

    private final PathRateLimitFilter filter = new PathRateLimitFilter();

    @Test
    void semicolonPathParametersDoNotBypassTheLimiter() {
        final MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/profile;/76561198000000000");
        req.setRequestURI("/api/profile;/76561198000000000");
        assertFalse(filter.shouldNotFilter(req));
    }

    @Test
    void otherApiPathsAreNotLimited() {
        final MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/review-game/today");
        req.setRequestURI("/api/review-game/today");
        assertTrue(filter.shouldNotFilter(req));
    }
}

class UserSearchRateLimitFilterTest {

    private final PathRateLimitFilter filter = new PathRateLimitFilter();

    @Test
    void percentEncodedPathDoesNotBypassTheLimiter() {
        final MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/users/%73earch");
        req.setRequestURI("/api/users/%73earch");
        assertFalse(filter.shouldNotFilter(req));
    }
}


class PathRateLimitFilterTest {

    private final PathRateLimitFilter filter = new PathRateLimitFilter();
    private final FilterChain chain = mock(FilterChain.class);

    private static MockHttpServletRequest req(String path, String method) {
        final MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRequestURI(path);
        request.setRemoteAddr("203.0.113.7");
        return request;
    }

    @Test
    void skipsOptionsPreflightsForEveryRule() {
        assertTrue(filter.shouldNotFilter(req("/api/users/search", "OPTIONS")));
        assertTrue(filter.shouldNotFilter(req("/api/profile/1", "OPTIONS")));
        assertTrue(filter.shouldNotFilter(req("/api/auth/validate", "OPTIONS")));
    }

    @Test
    void searchIsLimitedToThirtyPerMinuteWithTheShared429Body() throws Exception {
        for (int i = 0; i < 30; i++) {
            filter.doFilterInternal(req("/api/users/search", "GET"), new MockHttpServletResponse(), chain);
        }
        final MockHttpServletResponse limited = new MockHttpServletResponse();
        filter.doFilterInternal(req("/api/users/search", "GET"), limited, chain);

        assertEquals(429, limited.getStatus());
        assertEquals("application/json", limited.getContentType());
        assertEquals("{\"error\":\"rate_limit_exceeded\"}", limited.getContentAsString());
    }

    @Test
    void exhaustingOneRuleDoesNotLimitAnother() throws Exception {
        for (int i = 0; i < 31; i++) {
            filter.doFilterInternal(req("/api/users/search", "GET"), new MockHttpServletResponse(), chain);
        }
        final MockHttpServletResponse profile = new MockHttpServletResponse();
        filter.doFilterInternal(req("/api/profile/76561198000000000", "GET"), profile, chain);

        assertEquals(200, profile.getStatus());
    }

    @Test
    void profileBurstAboveTheOldSixtyPerMinuteBudgetStaysAllowed() throws Exception {
        // The profile rule is keyed on the Next server's shared address, so 60/min capped the
        // whole site's profile traffic; the budget must absorb an ordinary burst.
        for (int i = 0; i < 100; i++) {
            final MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilterInternal(req("/api/profile/76561198000000000", "GET"), response, chain);
            assertEquals(200, response.getStatus(), "request " + (i + 1) + " must not be rate limited");
        }
    }
}
