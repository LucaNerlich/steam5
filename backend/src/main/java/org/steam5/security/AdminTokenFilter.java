package org.steam5.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class AdminTokenFilter extends OncePerRequestFilter {

    private static final String ADMIN_HEADER = "X-Admin-Token";
    private static final String ADMIN_PATH_PREFIX = "/api/admin/";

    private final String expectedToken;

    public AdminTokenFilter(@Value("${admin.api-token:}") String expectedToken) {
        this.expectedToken = expectedToken;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (StringUtils.hasText(contextPath) && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        return "OPTIONS".equalsIgnoreCase(request.getMethod()) || !path.startsWith(ADMIN_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        if (!StringUtils.hasText(expectedToken)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
            return;
        }

        String providedToken = request.getHeader(ADMIN_HEADER);
        if (!StringUtils.hasText(providedToken) || !expectedToken.equals(providedToken)) {
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

