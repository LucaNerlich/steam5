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
 * #706: `unless` runs on the value Spring unwraps from the Optional (ObjectUtils.unwrapOptional),
 * so `#result == null` is the only safe emptiness check — calling a method on the unwrapped
 * result throws. Drive the real proxy, initialised the way the container initialises it.
 */
class PlayerSpotlightCachingTest {

    @Test
    void anAbsentSpotlightIsNotCached() {
        final PlayerSpotlightRepository repository = mock(PlayerSpotlightRepository.class);
        when(repository.findById(GameDate.todayUtc())).thenReturn(Optional.empty());

        final PlayerSpotlightService proxied = proxyFor(repository);

        assertTrue(proxied.getTodaySpotlight().isEmpty());
        assertTrue(proxied.getTodaySpotlight().isEmpty());

        verify(repository, times(2)).findById(GameDate.todayUtc());
    }

    @Test
    void aPresentSpotlightIsCachedAndServed() {
        // Without this case the test cannot tell "not cached" from "caching never ran".
        final PlayerSpotlightRepository repository = mock(PlayerSpotlightRepository.class);
        final org.steam5.domain.PlayerSpotlight row = new org.steam5.domain.PlayerSpotlight();
        row.setGameDate(GameDate.todayUtc());
        row.setSteamId("76561198028075069");
        row.setInsightType(org.steam5.domain.PlayerSpotlightInsightType.DAY_STREAK);
        row.setHeadline("Day streak");
        row.setDetail("5 days in a row");
        row.setStatLabel("5");
        when(repository.findById(GameDate.todayUtc())).thenReturn(Optional.of(row));

        final PlayerSpotlightService proxied = proxyFor(repository);

        assertTrue(proxied.getTodaySpotlight().isPresent());
        assertTrue(proxied.getTodaySpotlight().isPresent());

        verify(repository, times(1)).findById(GameDate.todayUtc());
    }

    private static PlayerSpotlightService proxyFor(PlayerSpotlightRepository repository) {
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
        // CacheAspectSupport.execute() bypasses caching until the bean lifecycle runs; without
        // these two calls the test silently exercises no cache at all.
        interceptor.afterPropertiesSet();
        interceptor.afterSingletonsInstantiated();
        return (PlayerSpotlightService) factory.getProxy();
    }
}
