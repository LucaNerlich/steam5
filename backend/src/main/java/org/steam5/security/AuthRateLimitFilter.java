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
 * Fix #5: Per-IP rate limiter for all {@code /api/auth/**} endpoints.
 *
 * <p>Uses a Caffeine cache (already on the classpath) to keep a per-IP request
 * counter that resets 1 minute after the <em>first</em> request from that IP in
 * the current window.  No new dependency is required.</p>
 *
 * <p>Limits are intentionally generous ({@value #MAX_REQUESTS_PER_MINUTE}/min)
 * so that normal browser behaviour (page loads, SWR polls) is never affected.
 * The limit prevents high-frequency token enumeration and DoS amplification
 * against the validate endpoint in particular.</p>
 */
@Component
@Slf4j
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final String AUTH_PATH_PREFIX = "/api/auth/";
    private static final int MAX_REQUESTS_PER_MINUTE = 60;

    /**
     * Tracks request counts per IP.  Each entry expires 1 minute after it was
     * first written, giving a fixed-window behaviour anchored at the first
     * request from that IP.  The cache is bounded to 10 000 entries to prevent
     * unbounded memory growth during an IP-distributed flood.
     */
    private final Cache<String, AtomicInteger> requestCounts = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(1))
            .maximumSize(10_000)
            .build();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        final String contextPath = request.getContextPath();
        if (StringUtils.hasText(contextPath) && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        // Skip OPTIONS (CORS preflight) and any path outside /api/auth/
        return "OPTIONS".equalsIgnoreCase(request.getMethod()) || !path.startsWith(AUTH_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        // server.forward-headers-strategy=framework ensures getRemoteAddr() returns
        // the real client IP after X-Forwarded-For processing.
        final String ip = request.getRemoteAddr();
        final AtomicInteger count = requestCounts.get(ip, k -> new AtomicInteger(0));

        if (count.incrementAndGet() > MAX_REQUESTS_PER_MINUTE) {
            log.warn("Auth rate limit exceeded: ip={} uri={}", ip, request.getRequestURI());
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"rate_limit_exceeded\"}");
            return;
        }

        chain.doFilter(request, response);
    }
}
