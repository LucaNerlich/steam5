package org.steam5.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.steam5.service.PresenceRateLimiter;

import java.io.IOException;

/**
 * Per-IP rate limiter for {@code POST /api/ws/ticket}.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PresenceRateLimitFilter extends OncePerRequestFilter {

    private static final String TICKET_PATH = "/api/ws/ticket";

    private final PresenceRateLimiter presenceRateLimiter;

    /**
     * Determines whether the request should bypass presence ticket rate limiting.
     *
     * @param request the incoming HTTP request
     * @return {@code true} unless the request is a {@code POST} to {@code /api/ws/ticket}
     */
    @Override
    protected boolean shouldNotFilter(final HttpServletRequest request) {
        String path = request.getRequestURI();
        final String contextPath = request.getContextPath();
        if (StringUtils.hasText(contextPath) && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        return !"POST".equalsIgnoreCase(request.getMethod()) || !TICKET_PATH.equals(path);
    }

    /**
     * Enforces the presence ticket rate limit for the request's client IP.
     *
     * @param request  the incoming HTTP request
     * @param response the HTTP response
     * @param chain    the filter chain
     * @throws IOException      if writing the response or continuing the chain fails
     * @throws ServletException if continuing the filter chain fails
     */
    @Override
    protected void doFilterInternal(final HttpServletRequest request,
                                    final HttpServletResponse response,
                                    final FilterChain chain) throws ServletException, IOException {
        final String ip = request.getRemoteAddr();
        if (!presenceRateLimiter.tryAcquireTicket(ip)) {
            log.warn("Presence ticket rate limit exceeded: ip={}", ip);
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"rate_limit_exceeded\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
