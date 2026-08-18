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
}

class ProfileRateLimitFilterTest {

    private final ProfileRateLimitFilter filter = new ProfileRateLimitFilter();

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
