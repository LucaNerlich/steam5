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
    void currentStreaksAreComputedAsTrailingRunsRatherThanEveryPlayedDay() throws Exception {
        final String sql = query("findCurrentStreaks", java.util.List.class, LocalDate.class);
        assertTrue(sql.contains("row_number() over (partition by steam_id order by game_date)"), sql);
        assertTrue(sql.contains("group by steam_id, grp"), sql);
        assertTrue(sql.contains("where end_date >= cast(:asOfDate as date) - 1"), sql);
    }

    @Test
    void playerStreakStatsAreOneRowPerUserWithBothStreaksAndRecentDates() throws Exception {
        final String sql = query("findPlayerStreakStats", java.util.List.class, LocalDate.class);
        assertTrue(sql.contains("row_number() over (partition by steam_id order by game_date desc)"), sql);
        assertTrue(sql.contains("\"lastPlayed\""), sql);
        assertTrue(sql.contains("\"previousPlayed\""), sql);
        assertTrue(sql.contains("\"currentStreak\""), sql);
        assertTrue(sql.contains("\"longestStreak\""), sql);
    }

    @Test
    void seasonLongestStreaksAreAggregatedInSql() throws Exception {
        final String sql = query("findSeasonLongestStreaks", LocalDate.class, LocalDate.class);
        assertTrue(sql.contains("select distinct g.steam_id"), sql);
        assertTrue(sql.contains("max(len) as \"longestStreak\""), sql);
    }

    @Test
    void spotlightRecencyCountsRowsWithoutSelectingThem() throws Exception {
        final String sql = query("countRoundsBySteamIdInAndGameDateBetween",
                java.util.List.class, LocalDate.class, LocalDate.class);
        assertTrue(sql.contains("count(g) as rounds"), sql);
        assertTrue(sql.contains("group by g.steamId"), sql);
    }

    @Test
    void profileStatsAndHistoryAreScopedToTheRequestedUser() throws Exception {
        final String statsSql = query("findAllTimeStatsForUser", String.class);
        assertTrue(statsSql.contains("WHERE g.steam_id = :steamId"), statsSql);
        assertTrue(statsSql.contains("SUM(g.points)"), statsSql);

        final String historySql = query("findRecentBySteamIdOrderByGameDateDescRoundIndexAsc",
                String.class, LocalDate.class, LocalDate.class);
        assertTrue(historySql.contains("g.gameDate between :startDate and :endDate"), historySql);
    }

    @Test
    void dailyAveragesRequireFiveDistinctPlayers() throws Exception {
        assertTrue(query("findDailyAvgScoresDesc").contains("HAVING COUNT(DISTINCT steam_id) >= 5"));
        assertTrue(query("findDailyAvgScoresAsc").contains("HAVING COUNT(DISTINCT steam_id) >= 5"));
        assertTrue(query("findDailyAvgScoresInRange", LocalDate.class, LocalDate.class)
                .contains("HAVING COUNT(DISTINCT steam_id) >= 5"));
    }
}
