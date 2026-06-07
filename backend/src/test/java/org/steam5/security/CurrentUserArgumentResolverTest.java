package org.steam5.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.web.context.request.NativeWebRequest;
import org.steam5.service.AuthTokenService;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Exercises the token-extraction seam directly: the bearer-header / cookie-fallback
 * precedence that was previously copy-pasted into every authenticated handler.
 */
class CurrentUserArgumentResolverTest {

    private AuthTokenService authTokenService;
    private CurrentUserArgumentResolver resolver;

    @BeforeEach
    void setUp() {
        authTokenService = mock(AuthTokenService.class);
        resolver = new CurrentUserArgumentResolver(authTokenService);
    }

    private static NativeWebRequest request(final String authHeader, final Cookie... cookies) {
        final NativeWebRequest webRequest = mock(NativeWebRequest.class);
        when(webRequest.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn(authHeader);
        final HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        when(servletRequest.getCookies()).thenReturn(cookies.length == 0 ? null : cookies);
        when(webRequest.getNativeRequest(HttpServletRequest.class)).thenReturn(servletRequest);
        return webRequest;
    }

    @Test
    void resolvesBearerHeaderToken() {
        when(authTokenService.verifyToken("tok")).thenReturn("u1");
        final Object result = resolver.resolveArgument(null, null, request("Bearer tok"), null);
        assertEquals("u1", result);
    }

    @Test
    void fallsBackToCookieWhenHeaderIsNotBearer() {
        when(authTokenService.verifyToken("ctok")).thenReturn("u2");
        final Object result = resolver.resolveArgument(null, null,
                request("Basic xxx", new Cookie("s5_token", "ctok")), null);
        assertEquals("u2", result);
    }

    @Test
    void usesCookieWhenNoHeaderPresent() {
        when(authTokenService.verifyToken("ctok")).thenReturn("u3");
        final Object result = resolver.resolveArgument(null, null,
                request(null, new Cookie("s5_token", "ctok")), null);
        assertEquals("u3", result);
    }

    @Test
    void bearerHeaderTakesPrecedenceOverCookie() {
        when(authTokenService.verifyToken("htok")).thenReturn("u4");
        final Object result = resolver.resolveArgument(null, null,
                request("Bearer htok", new Cookie("s5_token", "ctok")), null);
        assertEquals("u4", result);
        verify(authTokenService).verifyToken("htok");
        verify(authTokenService, never()).verifyToken("ctok");
    }

    @Test
    void ignoresBlankCookieValue() {
        final Object result = resolver.resolveArgument(null, null,
                request(null, new Cookie("s5_token", "")), null);
        assertNull(result);
        verifyNoInteractions(authTokenService);
    }

    @Test
    void returnsNullWhenNoTokenAnywhere() {
        final Object result = resolver.resolveArgument(null, null, request(null), null);
        assertNull(result);
        verifyNoInteractions(authTokenService);
    }

    @Test
    void supportsAnnotatedStringParameter() throws Exception {
        final Method m = Sample.class.getDeclaredMethod("annotated", String.class);
        assertTrue(resolver.supportsParameter(new MethodParameter(m, 0)));
    }

    @Test
    void doesNotSupportUnannotatedOrNonStringParameter() throws Exception {
        final Method plain = Sample.class.getDeclaredMethod("plain", String.class);
        assertFalse(resolver.supportsParameter(new MethodParameter(plain, 0)));
        final Method wrongType = Sample.class.getDeclaredMethod("annotatedLong", Long.class);
        assertFalse(resolver.supportsParameter(new MethodParameter(wrongType, 0)));
    }

    @SuppressWarnings("unused")
    static class Sample {
        void annotated(@CurrentUser String steamId) {
        }

        void plain(String steamId) {
        }

        void annotatedLong(@CurrentUser Long steamId) {
        }
    }
}
