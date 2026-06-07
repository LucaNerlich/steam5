package org.steam5.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.steam5.service.AuthTokenService;

/**
 * Resolves {@link CurrentUser}-annotated {@code String} parameters to the verified
 * steamId of the requesting user, or {@code null} when no valid token is present.
 *
 * <p>This concentrates the bearer-header / cookie-fallback token extraction that was
 * previously copy-pasted into every authenticated handler.</p>
 */
@Component
@RequiredArgsConstructor
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    private static final String COOKIE_NAME = "s5_token";

    private final AuthTokenService authTokenService;

    @Override
    public boolean supportsParameter(final MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUser.class)
                && String.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(final MethodParameter parameter,
                                  final ModelAndViewContainer mavContainer,
                                  final NativeWebRequest webRequest,
                                  final WebDataBinderFactory binderFactory) {
        final String token = extractToken(webRequest);
        return token == null ? null : authTokenService.verifyToken(token);
    }

    private static String extractToken(final NativeWebRequest webRequest) {
        final String bearer = webRequest.getHeader(HttpHeaders.AUTHORIZATION);
        if (bearer != null && bearer.toLowerCase().startsWith("bearer ")) {
            return bearer.substring(7).trim();
        }
        final HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        if (request != null && request.getCookies() != null) {
            for (final Cookie cookie : request.getCookies()) {
                if (COOKIE_NAME.equals(cookie.getName())) {
                    final String value = cookie.getValue();
                    if (value != null && !value.isBlank()) {
                        return value;
                    }
                }
            }
        }
        return null;
    }
}
