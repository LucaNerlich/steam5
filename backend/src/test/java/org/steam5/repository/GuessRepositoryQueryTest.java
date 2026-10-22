package org.steam5.repository;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the two SQL semantics behind #622/#635 without a database harness (the repo has no
 * Testcontainers setup): a future edit that reintroduces the old aggregate must fail here.
 */
class GuessRepositoryQueryTest {

    private static String query(String method, Class<?>... params) throws Exception {
        final Query annotation = GuessRepository.class.getMethod(method, params).getAnnotation(Query.class);
        assertNotNull(annotation, method + " lost its native query");
        return annotation.value();
    }

    @Test
    void perfectDayRoundsComeFromTheGeneratedPicks() throws Exception {
        for (final String sql : new String[]{
                query("findUsersByPerfectDaysDesc", int.class),
                query("findUsersByPerfectDaysDescInRange", LocalDate.class, LocalDate.class, int.class),
        }) {
            assertTrue(sql.contains("FROM review_game_pick"), sql);
            assertFalse(sql.contains("MAX(round_index)"), sql);
        }
    }

    @Test
    void perfectDaysMaterializedViewUsesTheSameRoundSource() throws Exception {
        try (InputStream in = GuessRepositoryQueryTest.class.getResourceAsStream("/db/mv-perfect-days.sql")) {
            assertNotNull(in);
            final String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(sql.contains("FROM review_game_pick"));
            assertFalse(sql.contains("MAX(round_index)"));
        }
    }

    @Test
    void dailyAveragesRequireFiveDistinctPlayers() throws Exception {
        assertTrue(query("findDailyAvgScoresDesc").contains("HAVING COUNT(DISTINCT steam_id) >= 5"));
        assertTrue(query("findDailyAvgScoresAsc").contains("HAVING COUNT(DISTINCT steam_id) >= 5"));
        assertTrue(query("findDailyAvgScoresInRange", LocalDate.class, LocalDate.class)
                .contains("HAVING COUNT(DISTINCT steam_id) >= 5"));
    }
}
