package org.steam5.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.steam5.security.CurrentUserArgumentResolver;

import java.util.List;

/**
 * Registers custom MVC argument resolvers, notably
 * {@link CurrentUserArgumentResolver} which backs the
 * {@link org.steam5.security.CurrentUser @CurrentUser} parameter annotation.
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final CurrentUserArgumentResolver currentUserArgumentResolver;

    @Override
    public void addArgumentResolvers(final List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentUserArgumentResolver);
    }
}
