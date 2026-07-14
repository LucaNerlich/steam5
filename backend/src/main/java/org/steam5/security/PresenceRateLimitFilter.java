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

    @Override
    protected boolean shouldNotFilter(final HttpServletRequest request) {
        String path = request.getRequestURI();
        final String contextPath = request.getContextPath();
        if (StringUtils.hasText(contextPath) && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        return !"POST".equalsIgnoreCase(request.getMethod()) || !TICKET_PATH.equals(path);
    }

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
