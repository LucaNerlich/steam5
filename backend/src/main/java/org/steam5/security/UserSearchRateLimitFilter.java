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
 * Per-IP rate limiter for {@code GET /api/users/search}.
 *
 * <p>This endpoint is public and searches users by persona name, which enables enumeration.
 * A fixed-window Caffeine counter (same approach as {@link AuthRateLimitFilter}) caps how many
 * lookups a single IP can perform per minute without affecting normal debounced-typing usage.</p>
 */
@Component
@Slf4j
public class UserSearchRateLimitFilter extends OncePerRequestFilter {

    private static final String SEARCH_PATH = "/api/users/search";
    private static final int MAX_REQUESTS_PER_MINUTE = 30;

    private final FixedWindowCounter requestCounts = new FixedWindowCounter(MAX_REQUESTS_PER_MINUTE);

    @Override
    protected boolean shouldNotFilter(final HttpServletRequest request) {
        return "OPTIONS".equalsIgnoreCase(request.getMethod())
                || !SEARCH_PATH.equals(RequestPathNormalizer.normalizedPath(request));
    }

    @Override
    protected void doFilterInternal(final HttpServletRequest request,
                                    final HttpServletResponse response,
                                    final FilterChain chain) throws ServletException, IOException {
        final String ip = request.getRemoteAddr();
        if (!requestCounts.tryAcquire(ip)) {
            log.warn("User search rate limit exceeded");
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"rate_limit_exceeded\"}");
            return;
        }

        chain.doFilter(request, response);
    }
}
