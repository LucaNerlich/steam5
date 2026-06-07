package org.steam5.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Resolves a controller {@code String} parameter to the authenticated user's steamId,
 * or {@code null} when the request carries no valid session token.
 *
 * <p>The token is read from the {@code Authorization: Bearer …} header, falling back
 * to the {@code s5_token} cookie, and verified via
 * {@link org.steam5.service.AuthTokenService}. This is the single place that knows how
 * a request conveys identity; handlers receive the resolved steamId and decide what an
 * absent identity means (typically a 401).</p>
 *
 * @see CurrentUserArgumentResolver
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUser {
}
