package org.steam5.web;

import org.junit.jupiter.api.Test;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
