package org.steam5.web;

import org.junit.jupiter.api.Test;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import org.steam5.domain.Season;
import org.steam5.service.SeasonService;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SeasonControllerCacheKeyTest {

    @Test
    void collapsesNumericSpellingsToTheSameCacheKey() {
        assertEquals("1", SeasonController.canonicalSeasonKey("1"));
        assertEquals("1", SeasonController.canonicalSeasonKey("01"));
        assertEquals("1", SeasonController.canonicalSeasonKey("+1"));
    }

    @Test
    void leavesNonNumericIdentifiersAlone() {
        // The key must be exactly what resolveSeason receives; trimming would let " 1"
        // reuse the cached body for "1" while a cold request returns 404.
        assertEquals(" season-one ", SeasonController.canonicalSeasonKey(" season-one "));
        assertEquals(" 1", SeasonController.canonicalSeasonKey(" 1"));
    }

    @Test
    void evaluatesTheCacheableKeyExpressionThroughSpel() throws Exception {
        final Cacheable cacheable = SeasonController.class
                .getMethod("seasonDetail", String.class)
                .getAnnotation(Cacheable.class);
        final Expression expression = new SpelExpressionParser().parseExpression(cacheable.key());
        final StandardEvaluationContext context = new StandardEvaluationContext();
        context.setVariable("seasonIdentifier", "01");
        assertEquals("1", expression.getValue(context, String.class));
        context.setVariable("seasonIdentifier", "+1");
        assertEquals("1", expression.getValue(context, String.class));
    }

    @Test
    void awardsResolvesTheNumericSegmentAsASeasonNumber() {
        final SeasonService seasonService = mock(SeasonService.class);
        final SeasonResponseMapper mapper = mock(SeasonResponseMapper.class);
        final Season byNumber = new Season();
        byNumber.setId(7L);
        final Season byId = new Season();
        byId.setId(1L);
        when(seasonService.findSeasonByNumber(1)).thenReturn(Optional.of(byNumber));
        when(seasonService.findSeason(1L)).thenReturn(Optional.of(byId));
        when(seasonService.listAwardsForSeason(7L)).thenReturn(List.of());
        when(mapper.mapAwards(anyList())).thenReturn(List.of());

        new SeasonController(seasonService, mapper).awards("1");

        verify(seasonService).listAwardsForSeason(7L);
        verify(seasonService, never()).listAwardsForSeason(1L);
    }

    @Test
    void awardsResolveAndCacheWithTheSameIdentifierRuleAsDetail() throws Exception {
        // #617: the awards route must not resolve a numeric segment as a primary key.
        final java.lang.reflect.Method method = SeasonController.class.getMethod("awards", String.class);
        final Cacheable cacheable = method.getAnnotation(Cacheable.class);
        final Expression expression = new SpelExpressionParser().parseExpression(cacheable.key());
        final StandardEvaluationContext context = new StandardEvaluationContext();
        context.setVariable("seasonId", "01");
        assertEquals("1", expression.getValue(context, String.class));
    }
}
