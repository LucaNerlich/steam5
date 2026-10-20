package org.steam5.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
