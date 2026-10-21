package org.steam5.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Per-IP fixed-window rate limits for the public API paths that need one.
 *
 * <p>One servlet filter owns the shared OPTIONS bypass, the 429 body and the
 * client-IP rule; a path is limited only while it matches a rule below. The
 * presence ticket limiter stays separate because it is config-driven and shared
 * with the WebSocket handshake.</p>
 */
@Component
@Slf4j
public class PathRateLimitFilter extends OncePerRequestFilter {

    private record Rule(String path, boolean exact, FixedWindowCounter counter, String label) {
    }

    private final List<Rule> rules = List.of(
            new Rule("/api/auth/", false, new FixedWindowCounter(60), "Auth"),
            new Rule("/api/profile/", false, new FixedWindowCounter(60), "Profile"),
            new Rule("/api/users/search", true, new FixedWindowCounter(30), "User search"));

    @Override
    protected boolean shouldNotFilter(final HttpServletRequest request) {
        return "OPTIONS".equalsIgnoreCase(request.getMethod()) || matchingRule(request) == null;
    }

    @Override
    protected void doFilterInternal(final HttpServletRequest request,
                                    final HttpServletResponse response,
                                    final FilterChain chain) throws ServletException, IOException {
        final Rule rule = matchingRule(request);
        if (rule != null && !rule.counter().tryAcquire(request.getRemoteAddr())) {
            log.warn("{} rate limit exceeded: ip={} uri={}", rule.label(), request.getRemoteAddr(), request.getRequestURI());
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"rate_limit_exceeded\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    private Rule matchingRule(final HttpServletRequest request) {
        final String path = RequestPathNormalizer.normalizedPath(request);
        for (Rule rule : rules) {
            if (rule.exact() ? rule.path().equals(path) : path.startsWith(rule.path())) {
                return rule;
            }
        }
        return null;
    }
}
