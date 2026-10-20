package org.steam5.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;

/**
 * Path matching helper shared by servlet filters that must agree with Spring MVC.
 *
 * <p>Spring MVC and {@code MvcRequestMatcher} strip semicolon path parameters per
 * segment before routing. Filters that inspect the raw {@code getRequestURI()}
 * would otherwise miss paths like {@code /api/admin;/seasons/backfill}.</p>
 */
public final class RequestPathNormalizer {

    private RequestPathNormalizer() {
    }

    public static String normalizedPath(HttpServletRequest request) {
        String path = request.getRequestURI();
        final String contextPath = request.getContextPath();
        if (StringUtils.hasText(contextPath) && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        // getRequestURI() is undecoded per the Servlet spec; redirects and MVC match the
        // decoded path, so decode before comparing (e.g. /api/users/%73earch).
        path = UriUtils.decode(path, StandardCharsets.UTF_8);
        final StringBuilder normalized = new StringBuilder(path.length());
        final String[] segments = path.split("/", -1);
        for (int i = 0; i < segments.length; i++) {
            String segment = segments[i];
            final int semi = segment.indexOf(';');
            if (semi >= 0) {
                segment = segment.substring(0, semi);
            }
            normalized.append(segment);
            if (i < segments.length - 1) {
                normalized.append('/');
            }
        }
        return normalized.toString();
    }
}
