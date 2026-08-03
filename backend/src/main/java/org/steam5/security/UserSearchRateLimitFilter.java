package org.steam5.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

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

    private final Cache<String, AtomicInteger> requestCounts = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(1))
            .maximumSize(10_000)
            .build();

    @Override
    protected boolean shouldNotFilter(final HttpServletRequest request) {
        String path = request.getRequestURI();
        final String contextPath = request.getContextPath();
        if (StringUtils.hasText(contextPath) && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        return "OPTIONS".equalsIgnoreCase(request.getMethod()) || !SEARCH_PATH.equals(path);
    }

    @Override
    protected void doFilterInternal(final HttpServletRequest request,
                                    final HttpServletResponse response,
                                    final FilterChain chain) throws ServletException, IOException {
        final String ip = request.getRemoteAddr();
        final AtomicInteger count = requestCounts.get(ip, k -> new AtomicInteger(0));

        if (count.incrementAndGet() > MAX_REQUESTS_PER_MINUTE) {
            log.warn("User search rate limit exceeded");
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"rate_limit_exceeded\"}");
            return;
        }

        chain.doFilter(request, response);
    }
}
