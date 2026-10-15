package org.steam5.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Per-IP rate limiter for {@code GET /api/profile/**}.
 *
 * <p>Profile reads load a user's complete guess history plus per-request app-name,
 * awards, and spotlight queries, and are public — hammering arbitrary steamIds
 * forces repeated full-history loads. A generous fixed-window cap (same Caffeine
 * approach as {@link UserSearchRateLimitFilter}) blocks abuse without affecting
 * normal browsing.</p>
 */
@Component
@Slf4j
public class ProfileRateLimitFilter extends OncePerRequestFilter {

    private static final String PROFILE_PATH_PREFIX = "/api/profile/";
    private static final int MAX_REQUESTS_PER_MINUTE = 60;

    private final FixedWindowCounter requestCounts = new FixedWindowCounter(MAX_REQUESTS_PER_MINUTE);

    @Override
    protected boolean shouldNotFilter(final HttpServletRequest request) {
        return "OPTIONS".equalsIgnoreCase(request.getMethod())
                || !RequestPathNormalizer.normalizedPath(request).startsWith(PROFILE_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(final HttpServletRequest request,
                                    final HttpServletResponse response,
                                    final FilterChain chain) throws ServletException, IOException {
        final String ip = request.getRemoteAddr();
        if (!requestCounts.tryAcquire(ip)) {
            log.warn("Profile rate limit exceeded");
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"rate_limit_exceeded\"}");
            return;
        }

        chain.doFilter(request, response);
    }
}
