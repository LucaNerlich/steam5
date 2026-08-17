package org.steam5.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

@Component
public class AdminTokenFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AdminTokenFilter.class);

    private static final String ADMIN_HEADER = "X-Admin-Token";
    private static final String ADMIN_PATH_PREFIX = "/api/admin/";
    private static final String METRICS_PATH_PREFIX = "/api/metrics/";
    private static final String CACHE_PATH_PREFIX = "/api/cache/";
    // Public-facing metrics endpoint consumed by the game statistics UI.
    private static final String PUBLIC_METRICS_PATH = "/api/metrics/picks/summary";

    private final String expectedToken;

    public AdminTokenFilter(@Value("${admin.api-token:}") String expectedToken) {
        this.expectedToken = expectedToken;
        if (!StringUtils.hasText(expectedToken)) {
            log.warn("ADMIN_API_TOKEN is not configured — all /api/admin/** requests will be rejected with 401. Set ADMIN_API_TOKEN in your environment.");
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        final String path = normalizedPath(request);
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        final boolean gated = path.startsWith(ADMIN_PATH_PREFIX)
                || path.startsWith(CACHE_PATH_PREFIX)
                || (path.startsWith(METRICS_PATH_PREFIX) && !PUBLIC_METRICS_PATH.equals(path));
        return !gated;
    }

    /**
     * Returns the request path with the context path removed and semicolon
     * path parameters stripped per segment, mirroring how Spring MVC and
     * Spring Security's {@code MvcRequestMatcher} normalize URIs before
     * matching. Without this, {@code /api/admin;/seasons/backfill} would
     * route to the admin controller while skipping this filter's raw
     * {@code getRequestURI()} check.
     */
    private static String normalizedPath(HttpServletRequest request) {
        String path = request.getRequestURI();
        final String contextPath = request.getContextPath();
        if (StringUtils.hasText(contextPath) && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        final StringBuilder normalized = new StringBuilder(path.length());
        final String[] segments = path.split("/", -1);
        for (int i = 0; i < segments.length; i++) {
            String segment = segments[i];
            final int semi = segment.indexOf(';');
            if (semi >= 0) {
                segment = segment.substring(0, semi);
            }
            normalized.append(segment);
            if (i < segments.length - 1) normalized.append('/');
        }
        return normalized.toString();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        if (!StringUtils.hasText(expectedToken)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
            return;
        }

        final String providedToken = request.getHeader(ADMIN_HEADER);

        // Fix #3: use a constant-time comparison to prevent timing-oracle attacks
        // that could reveal the token character-by-character via response latency.
        // String.equals() short-circuits on the first differing byte, leaking timing info.
        if (!StringUtils.hasText(providedToken)
                || !MessageDigest.isEqual(
                        expectedToken.getBytes(StandardCharsets.UTF_8),
                        providedToken.getBytes(StandardCharsets.UTF_8))) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
            return;
        }

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(
                "admin-token",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        ));
        SecurityContextHolder.setContext(context);

        filterChain.doFilter(request, response);
    }
}
