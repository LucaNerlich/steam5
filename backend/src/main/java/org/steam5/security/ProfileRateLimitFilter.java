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
        return "OPTIONS".equalsIgnoreCase(request.getMethod()) || !path.startsWith(PROFILE_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(final HttpServletRequest request,
                                    final HttpServletResponse response,
                                    final FilterChain chain) throws ServletException, IOException {
        final String ip = request.getRemoteAddr();
        final AtomicInteger count = requestCounts.get(ip, k -> new AtomicInteger(0));

        if (count.incrementAndGet() > MAX_REQUESTS_PER_MINUTE) {
            log.warn("Profile rate limit exceeded");
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"rate_limit_exceeded\"}");
            return;
        }

        chain.doFilter(request, response);
    }
}
