package org.steam5.game.year;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class YearGuessEvaluatorTest {

    private YearGameConfig config;

    @BeforeEach
    void setUp() {
        config = new YearGameConfig();
        config.setBucketBoundaries(List.of(1999, 2009, 2019));
    }

    @Test
    void inferBucket_mapsYearsToConfiguredRanges() {
        assertEquals("1-1999", YearGuessEvaluator.inferBucket(1995, config));
        assertEquals("2000-2009", YearGuessEvaluator.inferBucket(2005, config));
        assertEquals("2010-2019", YearGuessEvaluator.inferBucket(2015, config));
        assertEquals("2019+", YearGuessEvaluator.inferBucket(2024, config));
    }

    @Test
    void scorePoints_usesDistanceBasedScoring() {
        final List<String> buckets = List.of("1-1999", "2000-2009", "2010-2019", "2019+");
        assertEquals(5, YearGuessEvaluator.scorePoints(buckets, "2000-2009", "2000-2009"));
        assertEquals(3, YearGuessEvaluator.scorePoints(buckets, "2000-2009", "2010-2019"));
        assertEquals(1, YearGuessEvaluator.scorePoints(buckets, "2000-2009", "2019+"));
        assertEquals(0, YearGuessEvaluator.scorePoints(buckets, "1-1999", "2019+"));
    }

    @Test
    void isCorrectForLabel_matchesInclusiveRanges() {
        assertTrue(YearGuessEvaluator.isCorrectForLabel("2000-2009", 2000));
        assertTrue(YearGuessEvaluator.isCorrectForLabel("2000-2009", 2009));
        assertFalse(YearGuessEvaluator.isCorrectForLabel("2000-2009", 2010));
        assertTrue(YearGuessEvaluator.isCorrectForLabel("2019+", 2026));
    }
}
