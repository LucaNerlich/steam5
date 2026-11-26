package org.steam5.service;

import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.cache.annotation.AnnotationCacheOperationSource;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.cache.interceptor.CacheInterceptor;
import org.steam5.domain.GameDate;
import org.steam5.repository.CommentReactionRepository;
import org.steam5.repository.CommentRepository;
import org.steam5.repository.GuessRepository;
import org.steam5.repository.PlayerSpotlightRepository;
import org.steam5.repository.UserRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * #706: `unless = "#result == null"` on an Optional cached Optional.empty() for the full TTL, so
 * a request made before the nightly job hid the spotlight for five extra minutes. The annotation
 * is invisible to the compiler, so drive the real proxy.
 */
class PlayerSpotlightCachingTest {

    @Test
    void anAbsentSpotlightIsNotCached() {
        final PlayerSpotlightRepository repository = mock(PlayerSpotlightRepository.class);
        when(repository.findById(GameDate.todayUtc())).thenReturn(Optional.empty());
        final PlayerSpotlightService target = new PlayerSpotlightService(
                mock(GuessRepository.class),
                mock(UserRepository.class),
                mock(StatisticsService.class),
                repository,
                mock(CommentRepository.class),
                mock(CommentReactionRepository.class));

        final ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager("stats-short");
        final ProxyFactory factory = new ProxyFactory(target);
        factory.setProxyTargetClass(true);
        final CacheInterceptor interceptor = new CacheInterceptor();
        interceptor.setCacheOperationSource(new AnnotationCacheOperationSource());
        interceptor.setCacheManager(cacheManager);
        factory.addAdvice(interceptor);
        final PlayerSpotlightService proxied = (PlayerSpotlightService) factory.getProxy();

        assertTrue(proxied.getTodaySpotlight().isEmpty());
        assertTrue(proxied.getTodaySpotlight().isEmpty());

        // A cached empty Optional would answer the second call from the cache.
        verify(repository, times(2)).findById(GameDate.todayUtc());
    }
}
