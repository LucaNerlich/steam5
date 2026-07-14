package org.steam5.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.stereotype.Component;
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

    private static final AntPathRequestMatcher TICKET_MATCHER =
            new AntPathRequestMatcher("/api/ws/ticket", HttpMethod.POST.name());

    private final PresenceRateLimiter presenceRateLimiter;

    @Override
    protected boolean shouldNotFilter(final HttpServletRequest request) {
        return !TICKET_MATCHER.matches(request);
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
