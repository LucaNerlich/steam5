package org.steam5.game.year;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class YearGuessEvaluatorTest {

    private YearGameConfig config;

    @BeforeEach
    void setUp() {
        config = new YearGameConfig();
        config.setMaxPoints(5);
        config.setHintDistanceThresholds(java.util.List.of(12, 6, 2));
    }

    @Test
    void maxPoints_decreasesWithHintsUsed() {
        assertEquals(5, YearGuessEvaluator.maxPointsForHintsUsed(0, config));
        assertEquals(4, YearGuessEvaluator.maxPointsForHintsUsed(1, config));
        assertEquals(3, YearGuessEvaluator.maxPointsForHintsUsed(2, config));
        assertEquals(2, YearGuessEvaluator.maxPointsForHintsUsed(3, config));
    }

    @Test
    void hintUnlock_followsDistanceThresholds() {
        assertFalse(YearGuessEvaluator.isHintUnlocked(1, 11, config));
        assertTrue(YearGuessEvaluator.isHintUnlocked(1, 12, config));
        assertFalse(YearGuessEvaluator.isHintUnlocked(2, 5, config));
        assertTrue(YearGuessEvaluator.isHintUnlocked(2, 6, config));
        assertTrue(YearGuessEvaluator.isHintUnlocked(3, 2, config));
    }

    @Test
    void unlockableHints_requireSequentialReveal() {
        assertEquals(java.util.List.of(1), YearGuessEvaluator.unlockableHintLevels(15, 0, config));
        assertEquals(java.util.List.of(2), YearGuessEvaluator.unlockableHintLevels(8, 1, config));
        assertEquals(java.util.List.of(), YearGuessEvaluator.unlockableHintLevels(8, 0, config));
    }

    @Test
    void exactMatch_requiresSameYear() {
        assertTrue(YearGuessEvaluator.isExactMatch(2020, 2020));
        assertFalse(YearGuessEvaluator.isExactMatch(2019, 2020));
        assertEquals(1, YearGuessEvaluator.distance(2019, 2020));
    }
}
