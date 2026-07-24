# Hardest Games Materialized View Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a fifth materialized view, `mv_hardest_games`, backing `GET /api/stats/game/hardest` (currently a live, full-table-scan CTE query gated only by a 1-hour Caffeine cache), refreshed once daily. Give it full parity with the other four leaderboards: auto-bootstrap, the `X-Leaderboard-Refreshed-At` freshness header, and the frontend "Last updated" line.

**Architecture:** `mv_hardest_games` reproduces `GuessRepository#findHardestGames(limit, minPlayers)`'s query exactly, with `minPlayers` fixed at `5` (the only value ever passed today) and no `LIMIT` (materializes every qualifying game; the app applies `limit` in Java as it always has). Every non-grouped joined column in this query is already wrapped in an aggregate (`MAX(...)`/`COALESCE(MAX(...))`), so — unlike the original 4 MVs before their fix — it never relies on the functional-dependency-on-primary-key GROUP BY optimization; it still has an unavoidable table-level dependency on `guesses`/`steam_app_index`. The feature reuses the existing generalized machinery end to end rather than forking a parallel implementation: `LeaderboardType` gains a 5th constant, `LeaderboardMvRepository`/`LeaderboardRefreshService`/`LeaderboardRefreshJob`/`LeaderboardMvBootstrapConfig` each gain one more entry following their existing per-type pattern, and `StatisticsController` gets the same header helper `LeaderboardController` already has.

**Tech Stack:** Spring Boot 4.1 / Java 21 backend (Gradle), Spring Data JPA, Quartz; Next.js/TypeScript frontend, SWR, Vitest.

## Global Constraints

- **Test/build commands** (`cd` triggers a permission issue in some sandboxed sessions — always use the `-p`/`--prefix` form):
  - Backend fast loop: `/Users/nerlich/workspace/luca/steam5/backend/gradlew -p /Users/nerlich/workspace/luca/steam5/backend test --tests "<FQCN>"`.
  - Backend full suite: `/Users/nerlich/workspace/luca/steam5/backend/gradlew -p /Users/nerlich/workspace/luca/steam5/backend test`. Expect exactly one pre-existing, unrelated failure: `Steam5ApplicationTests#contextLoads` (`jakarta.websocket.server.ServerContainer` bean-creation issue). Any other failure is a real regression.
  - Frontend: `npm --prefix /Users/nerlich/workspace/luca/steam5/frontend test` (vitest) and `npx --prefix /Users/nerlich/workspace/luca/steam5/frontend tsc --noEmit -p /Users/nerlich/workspace/luca/steam5/frontend/tsconfig.json` (typecheck — no dedicated script exists). **Do not run `next build`** — it has previously triggered an unrelated auto-codemod that stray-edits `app/layout.tsx`; if you ever see `app/layout.tsx` modified without having touched it yourself, revert it with `git checkout -- frontend/app/layout.tsx` before committing.
- **`LeaderboardType`** (`org.steam5.domain`) gains a 5th constant: `ALL_TIME, MONTHLY, WEEKLY, SEASON, HARDEST_GAMES`. Order matters for the advisory-lock key derivation (`ADVISORY_LOCK_BASE + type.ordinal()` in `LeaderboardRefreshService`) but not for anything else — append it last so existing ordinals for the other 4 don't shift.
- **MV name (exact):** `mv_hardest_games`. **Unique index (exact):** `ux_mv_hardest_games_app_id`, on `(app_id)`. **DDL file (exact):** `backend/src/main/resources/db/mv-hardest-games.sql`.
- **MV column names (exact):** `app_id`, `app_name`, `avg_score`, `player_count`, `too_high_count`, `too_low_count`, `total_guesses`, `most_common_wrong_bucket`, `most_common_wrong_bucket_count`, `actual_bucket`, `latest_pick_date`.
- **`minPlayers` is fixed at `5`** in the MV's `HAVING` clause (matches the only value ever passed via `StatisticsService#getHardestGames` → `guessRepository.findHardestGames(limit, 5)` today). The MV has **no `LIMIT`** — ordered by `avg_score ASC, player_count DESC`, matching the existing native query's `ORDER BY` exactly. The Java-side `.limit(limit)` (applied in `StatisticsService#getHardestGames`) reproduces the old query's `LIMIT :limit` behavior.
- **`GuessRepository#findHardestGames(int, int)` and its `HardestGameRow` interface are deleted** once `StatisticsService#getHardestGames` migrates to the MV — confirmed via grep that `StatisticsService.getHardestGames` is their only caller anywhere in `backend/src/main`/`backend/src/test`.
- **New repository/service/job/bootstrap methods (exact names), all in the existing shared files, following the exact per-type pattern already used for `ALL_TIME`/`MONTHLY`/`WEEKLY`/`SEASON`:**
  - `LeaderboardMvRepository`: nested `HardestGameMvRow` projection (getters: `getAppId()/getAppName()/getAvgScore()/getPlayerCount()/getTooHighCount()/getTooLowCount()/getTotalGuesses()/getMostCommonWrongBucket()/getMostCommonWrongBucketCount()/getActualBucket()/getLatestPickDate()`), `findHardestGames()` (read), `refreshHardestGamesFull()`, `refreshHardestGamesConcurrently()`.
  - `LeaderboardRefreshService`: `refreshHardestGames()`, calling the same private `refresh(...)` helper with `LeaderboardType.HARDEST_GAMES` and view name `mv_hardest_games`.
  - `LeaderboardRefreshJob`: new `HARDEST_GAMES -> refreshService.refreshHardestGames();` switch case, new `@Bean("LeaderboardRefreshJob_HardestGames") JobDetail` method.
  - `LeaderboardMvBootstrapConfig`: new `MvDefinition` entry `(LeaderboardType.HARDEST_GAMES, "mv_hardest_games", "ux_mv_hardest_games_app_id", "db/mv-hardest-games.sql")`, appended to `MV_DEFINITIONS`.
- **Quartz trigger:** `triggerLeaderboardRefreshHardestGames`, cron `"0 48 0 * * ?"` (00:48 UTC, staggered after `season`'s 00:46) — **nightly only, no intraday trigger** ("once a day is enough"). Config property: `jobs.leaderboard-refresh-hardest-games.enabled`, default `true` in both `application.yml` (`${JOB_LEADERBOARD_REFRESH_HARDEST_GAMES:true}`) and `application-dev.yml` (literal `true`).
- **`StatisticsService#getHardestGames(int limit)`** keeps its existing `@Cacheable(value = "stats-hourly", key = "'hardest-games-' + #limit", unless = "#result == null")` annotation unchanged — only its internal data source changes, from `guessRepository.findHardestGames(limit, 5)` to `leaderboardMvRepository.findHardestGames()` + `.stream().limit(limit)`. The per-row mapping logic (deception rate/direction computation) is untouched.
- **`StatisticsController#hardestGames`** gains the same `X-Leaderboard-Refreshed-At` header as `LeaderboardController`, via a new `LeaderboardRefreshStateRepository` dependency and a locally-duplicated `withRefreshedAtHeader` helper (not a shared/extracted utility — this codebase's stated convention is to wait for a third occurrence before extracting a shared abstraction; this is only the second). Existing `Cache-Control` header on that endpoint is unchanged; the new header is added alongside it.
- **Frontend header name (exact):** `X-Leaderboard-Refreshed-At` (same header name reused across all leaderboard/stat endpoints — it's semantically identical: "when was this endpoint's underlying MV last refreshed").
- **`frontend/app/api/stats/game/hardest/route.ts`** gets the same header-forwarding fix already applied to the 4 leaderboard proxy routes this session. **`frontend/src/components/HardestGamesTable.tsx`** gets the same wrapped-SWR-fetcher + `formatRefreshedAt` "Last updated" paragraph as `LeaderboardTable.tsx` — always shown (no mode-based gating needed here, since this component only ever backs one MV-backed page). `frontend/src/lib/hardestGames.ts`'s `fetchHardestGames()` (server-side prefetch) is **not** changed — matches the earlier, already-approved decision to accept a brief post-hydration "pop-in" for the freshness text rather than threading the header through SSR `initialData`.
- **Tests:** neither `StatisticsService` nor `StatisticsController` has any existing test file — new, narrowly-scoped test files (`StatisticsServiceTest`, `StatisticsControllerTest`) cover only `getHardestGames`/`hardestGames()`, not the other ~15 untested methods on those classes. `LeaderboardRefreshServiceTest`, `LeaderboardRefreshJobTest`, and `LeaderboardMvBootstrapConfigTest` are extended for the 5th type — the bootstrap test's expected `Statement.execute()` counts change from 4-MV to 5-MV multiples (e.g. "neither exists" goes from `times(12)` to `times(15)`).

---

### Task 1: `LeaderboardType.HARDEST_GAMES` + `mv-hardest-games.sql` DDL

**Files:**
- Modify: `backend/src/main/java/org/steam5/domain/LeaderboardType.java`
- Create: `backend/src/main/resources/db/mv-hardest-games.sql`

**Interfaces:**
- Produces: `LeaderboardType.HARDEST_GAMES`, `mv_hardest_games` (columns per Global Constraints), consumed by every later task in this plan.
- Consumes: nothing new.

No automated test for the DDL file itself (matches the existing convention for `mv-leaderboard-*.sql` — manually-applied/bootstrap-read DDL, not unit-tested directly). Verification is a compile check plus a semicolon-count sanity check (the bootstrap config assumes exactly 2 statements per file).

- [ ] **Step 1: Add `HARDEST_GAMES` to `LeaderboardType.java`**

Find:

```java
public enum LeaderboardType {
    ALL_TIME, MONTHLY, WEEKLY, SEASON
}
```

Replace with:

```java
public enum LeaderboardType {
    ALL_TIME, MONTHLY, WEEKLY, SEASON, HARDEST_GAMES
}
```

- [ ] **Step 2: Create `mv-hardest-games.sql`**

```sql
-- Materialized view backing the hardest-games ranking read path
-- (StatisticsService#getHardestGames / GET /api/stats/game/hardest).
--
-- Reproduces GuessRepository#findHardestGames(limit, minPlayers) exactly, with minPlayers
-- fixed at 5 (the only value ever passed in production — see StatisticsService#getHardestGames)
-- and no LIMIT (the view materializes every qualifying game; the app applies LIMIT in Java,
-- same as it always has). Ranks games by difficulty (lowest average points first) with
-- deception metrics (too-high/too-low guess counts using the same leading-numeric-bucket
-- regex comparison as the leaderboard MVs) and the single most common wrong bucket per game
-- (via a DISTINCT ON CTE).
--
-- LEFT JOINs `steam_app_index` for the app's display name (falls back to the raw app_id if
-- missing) and joins a wrong-bucket CTE derived from `guesses` — every non-grouped column
-- from those is already wrapped in an aggregate (MAX(...)/COALESCE(MAX(...))), so this view
-- does NOT rely on Postgres's functional-dependency-on-primary-key GROUP BY optimization the
-- way the leaderboard MVs originally did — no extra catalog dependency on any primary key
-- constraint. It still has an unavoidable table-level dependency on `guesses`/`steam_app_index`
-- themselves (see leaderboard-mv-maintenance.sql — this view must be dropped the same way
-- before a pg_restore --clean).
--
-- NOT managed by Hibernate ddl-auto — created/populated automatically by
-- LeaderboardMvBootstrapConfig at startup (or apply manually, see README "Query Performance
-- Notes"). Refreshed by LeaderboardRefreshJob via REFRESH MATERIALIZED VIEW CONCURRENTLY,
-- which requires the unique index below and prior population.
--
-- Freshness is bounded by refresh cadence (jobs.leaderboard-refresh-hardest-games.enabled /
-- QuartzConfig) — once daily only, no intraday trigger (hardest-games rankings change slowly).
CREATE MATERIALIZED VIEW mv_hardest_games AS
WITH wrong_counts AS (
    SELECT app_id, selected_bucket, COUNT(*) AS cnt
    FROM guesses
    WHERE selected_bucket <> actual_bucket
    GROUP BY app_id, selected_bucket
),
top_wrong AS (
    SELECT DISTINCT ON (app_id) app_id, selected_bucket, cnt
    FROM wrong_counts
    ORDER BY app_id, cnt DESC
)
SELECT
    g.app_id                                                            AS app_id,
    COALESCE(MAX(sai.name), CAST(g.app_id AS TEXT))                     AS app_name,
    AVG(g.points)                                                       AS avg_score,
    COUNT(DISTINCT g.steam_id)                                          AS player_count,
    SUM(CASE WHEN
        CAST(NULLIF(regexp_replace(g.selected_bucket, '^(\d+).*', '\1'), '') AS BIGINT) >
        CAST(NULLIF(regexp_replace(g.actual_bucket,   '^(\d+).*', '\1'), '') AS BIGINT)
    THEN 1 ELSE 0 END)                                                  AS too_high_count,
    SUM(CASE WHEN
        CAST(NULLIF(regexp_replace(g.selected_bucket, '^(\d+).*', '\1'), '') AS BIGINT) <
        CAST(NULLIF(regexp_replace(g.actual_bucket,   '^(\d+).*', '\1'), '') AS BIGINT)
    THEN 1 ELSE 0 END)                                                  AS too_low_count,
    COUNT(*)                                                            AS total_guesses,
    MAX(tw.selected_bucket)                                             AS most_common_wrong_bucket,
    MAX(tw.cnt)                                                         AS most_common_wrong_bucket_count,
    MAX(g.actual_bucket)                                                AS actual_bucket,
    MAX(g.game_date)                                                    AS latest_pick_date
FROM guesses g
LEFT JOIN steam_app_index sai ON sai.app_id = g.app_id
LEFT JOIN top_wrong tw ON tw.app_id = g.app_id
GROUP BY g.app_id
HAVING COUNT(DISTINCT g.steam_id) >= 5
ORDER BY AVG(g.points) ASC, COUNT(DISTINCT g.steam_id) DESC
WITH NO DATA;

-- Required for REFRESH MATERIALIZED VIEW CONCURRENTLY. CREATE INDEX CONCURRENTLY cannot run
-- inside a transaction block — run this as its own statement, not wrapped in BEGIN/COMMIT.
CREATE UNIQUE INDEX CONCURRENTLY ux_mv_hardest_games_app_id
    ON mv_hardest_games (app_id);
```

- [ ] **Step 3: Verify the file has exactly 2 statements (matches `LeaderboardMvBootstrapConfig`'s assumption)**

```bash
grep -c ';' backend/src/main/resources/db/mv-hardest-games.sql
```

Expected: `2`.

- [ ] **Step 4: Compile check**

```bash
/Users/nerlich/workspace/luca/steam5/backend/gradlew -p /Users/nerlich/workspace/luca/steam5/backend compileJava compileTestJava
```

Expected: `BUILD SUCCESSFUL`. (A compile error here would mean some other file already assumed only 4 `LeaderboardType` constants exist via an exhaustive `switch` — if so, stop and report it rather than guessing a fix; no such `switch` is expected to exist yet at this point in the plan.)

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/org/steam5/domain/LeaderboardType.java backend/src/main/resources/db/mv-hardest-games.sql
git commit -m "feat(db): add mv_hardest_games materialized view + LeaderboardType.HARDEST_GAMES"
```

---

### Task 2: `LeaderboardMvRepository` — hardest-games read + refresh methods

**Files:**
- Modify: `backend/src/main/java/org/steam5/repository/LeaderboardMvRepository.java`

**Interfaces:**
- Consumes: `LeaderboardType.HARDEST_GAMES`, `mv_hardest_games` from Task 1.
- Produces: `HardestGameMvRow`, `findHardestGames()`, `refreshHardestGamesFull()`, `refreshHardestGamesConcurrently()`, consumed by Task 3 (`LeaderboardRefreshService`) and Task 7 (`StatisticsService`).

No dedicated test for this file (matches the existing convention — none of the other native-query methods in this repository have direct tests either; correctness is exercised indirectly through Task 7's service test). Verification is a compile check.

- [ ] **Step 1: Add the projection + methods**

Find:

```java
    @Query(value = "SELECT ispopulated FROM pg_matviews WHERE matviewname = :viewName", nativeQuery = true)
    Boolean isPopulated(@Param("viewName") String viewName);
```

Replace with:

```java
    interface HardestGameMvRow {
        Long getAppId();
        String getAppName();
        Double getAvgScore();
        Long getPlayerCount();
        Long getTooHighCount();
        Long getTooLowCount();
        Long getTotalGuesses();
        String getMostCommonWrongBucket();
        Long getMostCommonWrongBucketCount();
        String getActualBucket();
        java.time.LocalDate getLatestPickDate();
    }

    @Query(value = "SELECT app_id AS appId, app_name AS appName, avg_score AS avgScore, player_count AS playerCount, " +
            "too_high_count AS tooHighCount, too_low_count AS tooLowCount, total_guesses AS totalGuesses, " +
            "most_common_wrong_bucket AS mostCommonWrongBucket, most_common_wrong_bucket_count AS mostCommonWrongBucketCount, " +
            "actual_bucket AS actualBucket, latest_pick_date AS latestPickDate " +
            "FROM mv_hardest_games ORDER BY avg_score ASC, player_count DESC", nativeQuery = true)
    List<HardestGameMvRow> findHardestGames();

    @Query(value = "SELECT ispopulated FROM pg_matviews WHERE matviewname = :viewName", nativeQuery = true)
    Boolean isPopulated(@Param("viewName") String viewName);
```

Find:

```java
    @Transactional
    @Modifying(clearAutomatically = false, flushAutomatically = false)
    @Query(value = "REFRESH MATERIALIZED VIEW CONCURRENTLY mv_leaderboard_season", nativeQuery = true)
    void refreshSeasonConcurrently();
}
```

Replace with:

```java
    @Transactional
    @Modifying(clearAutomatically = false, flushAutomatically = false)
    @Query(value = "REFRESH MATERIALIZED VIEW CONCURRENTLY mv_leaderboard_season", nativeQuery = true)
    void refreshSeasonConcurrently();

    @Transactional
    @Modifying(clearAutomatically = false, flushAutomatically = false)
    @Query(value = "REFRESH MATERIALIZED VIEW mv_hardest_games", nativeQuery = true)
    void refreshHardestGamesFull();

    @Transactional
    @Modifying(clearAutomatically = false, flushAutomatically = false)
    @Query(value = "REFRESH MATERIALIZED VIEW CONCURRENTLY mv_hardest_games", nativeQuery = true)
    void refreshHardestGamesConcurrently();
}
```

- [ ] **Step 2: Compile check**

```bash
/Users/nerlich/workspace/luca/steam5/backend/gradlew -p /Users/nerlich/workspace/luca/steam5/backend compileJava
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/org/steam5/repository/LeaderboardMvRepository.java
git commit -m "feat(repository): add mv_hardest_games read/refresh methods to LeaderboardMvRepository"
```

---

### Task 3: `LeaderboardRefreshService#refreshHardestGames`

**Files:**
- Modify: `backend/src/main/java/org/steam5/service/LeaderboardRefreshService.java`
- Modify: `backend/src/test/java/org/steam5/service/LeaderboardRefreshServiceTest.java`

**Interfaces:**
- Consumes: `LeaderboardMvRepository` additions from Task 2.
- Produces: `LeaderboardRefreshService.refreshHardestGames()`, consumed by Task 4 (`LeaderboardRefreshJob`).

- [ ] **Step 1: Add a test for `refreshHardestGames()`**

Find (the last test method in the file):

```java
    @Test
    void refreshWeekly_whenPopulated_usesConcurrentRefreshAndRecordsState() {
        when(leaderboardMvRepository.isPopulated("mv_leaderboard_weekly")).thenReturn(true);

        service.refreshWeekly();

        verify(leaderboardMvRepository).refreshWeeklyConcurrently();
        verify(leaderboardMvRepository, never()).refreshWeeklyFull();

        ArgumentCaptor<LeaderboardRefreshState> captor = ArgumentCaptor.forClass(LeaderboardRefreshState.class);
        verify(refreshStateRepository).save(captor.capture());
        assertEquals(LeaderboardType.WEEKLY, captor.getValue().getLeaderboardType());
    }
}
```

Replace with:

```java
    @Test
    void refreshWeekly_whenPopulated_usesConcurrentRefreshAndRecordsState() {
        when(leaderboardMvRepository.isPopulated("mv_leaderboard_weekly")).thenReturn(true);

        service.refreshWeekly();

        verify(leaderboardMvRepository).refreshWeeklyConcurrently();
        verify(leaderboardMvRepository, never()).refreshWeeklyFull();

        ArgumentCaptor<LeaderboardRefreshState> captor = ArgumentCaptor.forClass(LeaderboardRefreshState.class);
        verify(refreshStateRepository).save(captor.capture());
        assertEquals(LeaderboardType.WEEKLY, captor.getValue().getLeaderboardType());
    }

    @Test
    void refreshHardestGames_whenPopulated_usesConcurrentRefreshAndRecordsState() {
        when(leaderboardMvRepository.isPopulated("mv_hardest_games")).thenReturn(true);

        service.refreshHardestGames();

        verify(leaderboardMvRepository).refreshHardestGamesConcurrently();
        verify(leaderboardMvRepository, never()).refreshHardestGamesFull();

        ArgumentCaptor<LeaderboardRefreshState> captor = ArgumentCaptor.forClass(LeaderboardRefreshState.class);
        verify(refreshStateRepository).save(captor.capture());
        assertEquals(LeaderboardType.HARDEST_GAMES, captor.getValue().getLeaderboardType());
    }

    @Test
    void refreshHardestGames_whenNotPopulated_fallsBackToFullRefresh() {
        when(leaderboardMvRepository.isPopulated("mv_hardest_games")).thenReturn(false);

        service.refreshHardestGames();

        verify(leaderboardMvRepository).refreshHardestGamesFull();
        verify(leaderboardMvRepository, never()).refreshHardestGamesConcurrently();
    }
}
```

- [ ] **Step 2: Run it to confirm it fails to compile**

```bash
/Users/nerlich/workspace/luca/steam5/backend/gradlew -p /Users/nerlich/workspace/luca/steam5/backend test --tests "org.steam5.service.LeaderboardRefreshServiceTest"
```

Expected: compile error — `service.refreshHardestGames()` doesn't exist yet.

- [ ] **Step 3: Add `refreshHardestGames()` to `LeaderboardRefreshService.java`**

Find:

```java
    @Transactional
    public void refreshSeason() {
        refresh(LeaderboardType.SEASON, "mv_leaderboard_season", leaderboardMvRepository::refreshSeasonConcurrently, leaderboardMvRepository::refreshSeasonFull);
    }
```

Replace with:

```java
    @Transactional
    public void refreshSeason() {
        refresh(LeaderboardType.SEASON, "mv_leaderboard_season", leaderboardMvRepository::refreshSeasonConcurrently, leaderboardMvRepository::refreshSeasonFull);
    }

    @Transactional
    public void refreshHardestGames() {
        refresh(LeaderboardType.HARDEST_GAMES, "mv_hardest_games", leaderboardMvRepository::refreshHardestGamesConcurrently, leaderboardMvRepository::refreshHardestGamesFull);
    }
```

- [ ] **Step 4: Run the test to confirm it passes**

```bash
/Users/nerlich/workspace/luca/steam5/backend/gradlew -p /Users/nerlich/workspace/luca/steam5/backend test --tests "org.steam5.service.LeaderboardRefreshServiceTest"
```

Expected: `BUILD SUCCESSFUL`, all 7 tests passing.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/org/steam5/service/LeaderboardRefreshService.java backend/src/test/java/org/steam5/service/LeaderboardRefreshServiceTest.java
git commit -m "feat(service): add LeaderboardRefreshService#refreshHardestGames"
```

---

### Task 4: `LeaderboardRefreshJob` — hardest-games dispatch + bean

**Files:**
- Modify: `backend/src/main/java/org/steam5/job/LeaderboardRefreshJob.java`
- Modify: `backend/src/test/java/org/steam5/job/LeaderboardRefreshJobTest.java`

**Interfaces:**
- Consumes: `LeaderboardRefreshService.refreshHardestGames()` from Task 3.
- Produces: `@Bean("LeaderboardRefreshJob_HardestGames") JobDetail`, consumed by Task 5 (`QuartzConfig`).

- [ ] **Step 1: Add a test for the `HARDEST_GAMES` dispatch**

Find:

```java
    @Test
    void execute_season_refreshesThenEvicts() throws JobExecutionException {
        job.execute(contextFor("SEASON"));
        verify(refreshService).refreshSeason();
        verify(cacheEvictor).evictLeaderboardStatic();
    }
```

Replace with:

```java
    @Test
    void execute_season_refreshesThenEvicts() throws JobExecutionException {
        job.execute(contextFor("SEASON"));
        verify(refreshService).refreshSeason();
        verify(cacheEvictor).evictLeaderboardStatic();
    }

    @Test
    void execute_hardestGames_refreshesThenEvicts() throws JobExecutionException {
        job.execute(contextFor("HARDEST_GAMES"));
        verify(refreshService).refreshHardestGames();
        verify(cacheEvictor).evictLeaderboardStatic();
    }
```

- [ ] **Step 2: Run it to confirm it fails to compile**

```bash
/Users/nerlich/workspace/luca/steam5/backend/gradlew -p /Users/nerlich/workspace/luca/steam5/backend test --tests "org.steam5.job.LeaderboardRefreshJobTest"
```

Expected: compile error — `refreshService.refreshHardestGames()` verification fails because `execute()` doesn't dispatch `HARDEST_GAMES` yet (the `switch` has no matching case, so `LeaderboardRefreshService` is never called for that type — this surfaces as a Mockito `WantedButNotInvoked` failure, not a compile error, since `refreshHardestGames()` itself already exists from Task 3; confirm the test fails for this reason).

- [ ] **Step 3: Add the switch case and the 5th `@Bean JobDetail`**

Find:

```java
            switch (type) {
                case ALL_TIME -> refreshService.refreshAllTime();
                case MONTHLY -> refreshService.refreshMonthly();
                case WEEKLY -> refreshService.refreshWeekly();
                case SEASON -> refreshService.refreshSeason();
            }
```

Replace with:

```java
            switch (type) {
                case ALL_TIME -> refreshService.refreshAllTime();
                case MONTHLY -> refreshService.refreshMonthly();
                case WEEKLY -> refreshService.refreshWeekly();
                case SEASON -> refreshService.refreshSeason();
                case HARDEST_GAMES -> refreshService.refreshHardestGames();
            }
```

Find:

```java
    @Bean("LeaderboardRefreshJob_Season")
    public JobDetail seasonJobDetail() {
        return JobBuilder.newJob().ofType(LeaderboardRefreshJob.class)
                .storeDurably()
                .withIdentity("LeaderboardRefreshJob_Season")
                .usingJobData("type", LeaderboardType.SEASON.name())
                .build();
    }
}
```

Replace with:

```java
    @Bean("LeaderboardRefreshJob_Season")
    public JobDetail seasonJobDetail() {
        return JobBuilder.newJob().ofType(LeaderboardRefreshJob.class)
                .storeDurably()
                .withIdentity("LeaderboardRefreshJob_Season")
                .usingJobData("type", LeaderboardType.SEASON.name())
                .build();
    }

    @Bean("LeaderboardRefreshJob_HardestGames")
    public JobDetail hardestGamesJobDetail() {
        return JobBuilder.newJob().ofType(LeaderboardRefreshJob.class)
                .storeDurably()
                .withIdentity("LeaderboardRefreshJob_HardestGames")
                .usingJobData("type", LeaderboardType.HARDEST_GAMES.name())
                .build();
    }
}
```

Also update the class Javadoc's stale "four JobDetail beans" reference:

Find:

```java
/**
 * Refreshes one leaderboard materialized view per firing, driven by the "type" JobDataMap
 * entry set on each of the four JobDetail beans below. A single parameterized job class
 * (rather than four near-identical ones) keeps the refresh-then-evict flow in one place.
 */
```

Replace with:

```java
/**
 * Refreshes one leaderboard materialized view per firing, driven by the "type" JobDataMap
 * entry set on each of the five JobDetail beans below. A single parameterized job class
 * (rather than five near-identical ones) keeps the refresh-then-evict flow in one place.
 */
```

- [ ] **Step 4: Run the test to confirm it passes**

```bash
/Users/nerlich/workspace/luca/steam5/backend/gradlew -p /Users/nerlich/workspace/luca/steam5/backend test --tests "org.steam5.job.LeaderboardRefreshJobTest"
```

Expected: `BUILD SUCCESSFUL`, all 8 tests passing.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/org/steam5/job/LeaderboardRefreshJob.java backend/src/test/java/org/steam5/job/LeaderboardRefreshJobTest.java
git commit -m "feat(job): dispatch LeaderboardRefreshJob to refreshHardestGames for the new type"
```

---

### Task 5: Quartz trigger + `jobs.leaderboard-refresh-hardest-games` config

**Files:**
- Modify: `backend/src/main/java/org/steam5/config/QuartzConfig.java`
- Modify: `backend/src/main/resources/application.yml`
- Modify: `backend/src/main/resources/application-dev.yml`

**Interfaces:**
- Consumes: `LeaderboardRefreshJob_HardestGames` bean from Task 4.
- Produces: nothing consumed by later tasks — self-contained.

No new test (matches the existing convention — no `QuartzConfig` trigger bean has a dedicated test). Verification is a compile check plus a full-suite run.

- [ ] **Step 1: Add the trigger bean**

Find:

```java
    @Bean
    @ConditionalOnProperty(prefix = "jobs.leaderboard-refresh-season", name = "enabled", havingValue = "true", matchIfMissing = false)
    public Trigger triggerLeaderboardRefreshSeason(@Qualifier("LeaderboardRefreshJob_Season") JobDetail job) {
        return TriggerBuilder.newTrigger().forJob(job)
                .withIdentity("LeaderboardRefreshJob_Season_Trigger")
                // daily at 00:46 UTC only — season boundary correctness matters more than
                // intraday freshness, so no additional intraday trigger for this type
                .withSchedule(
                        CronScheduleBuilder.cronSchedule("0 46 0 * * ?")
                                .inTimeZone(TimeZone.getTimeZone("UTC"))
                )
                .build();
    }
}
```

Replace with:

```java
    @Bean
    @ConditionalOnProperty(prefix = "jobs.leaderboard-refresh-season", name = "enabled", havingValue = "true", matchIfMissing = false)
    public Trigger triggerLeaderboardRefreshSeason(@Qualifier("LeaderboardRefreshJob_Season") JobDetail job) {
        return TriggerBuilder.newTrigger().forJob(job)
                .withIdentity("LeaderboardRefreshJob_Season_Trigger")
                // daily at 00:46 UTC only — season boundary correctness matters more than
                // intraday freshness, so no additional intraday trigger for this type
                .withSchedule(
                        CronScheduleBuilder.cronSchedule("0 46 0 * * ?")
                                .inTimeZone(TimeZone.getTimeZone("UTC"))
                )
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "jobs.leaderboard-refresh-hardest-games", name = "enabled", havingValue = "true", matchIfMissing = false)
    public Trigger triggerLeaderboardRefreshHardestGames(@Qualifier("LeaderboardRefreshJob_HardestGames") JobDetail job) {
        return TriggerBuilder.newTrigger().forJob(job)
                .withIdentity("LeaderboardRefreshJob_HardestGames_Trigger")
                // daily at 00:48 UTC only — rankings change slowly; no intraday trigger needed
                .withSchedule(
                        CronScheduleBuilder.cronSchedule("0 48 0 * * ?")
                                .inTimeZone(TimeZone.getTimeZone("UTC"))
                )
                .build();
    }
}
```

- [ ] **Step 2: Add the config key to `application.yml`**

Find:

```yaml
  leaderboard-refresh-season:
    enabled: ${JOB_LEADERBOARD_REFRESH_SEASON:true}
```

Replace with:

```yaml
  leaderboard-refresh-season:
    enabled: ${JOB_LEADERBOARD_REFRESH_SEASON:true}
  leaderboard-refresh-hardest-games:
    enabled: ${JOB_LEADERBOARD_REFRESH_HARDEST_GAMES:true}
```

- [ ] **Step 3: Add the config key to `application-dev.yml`**

Find:

```yaml
  leaderboard-refresh-season:
    enabled: true
```

Replace with:

```yaml
  leaderboard-refresh-season:
    enabled: true
  leaderboard-refresh-hardest-games:
    enabled: true
```

- [ ] **Step 4: Compile check + full backend suite**

```bash
/Users/nerlich/workspace/luca/steam5/backend/gradlew -p /Users/nerlich/workspace/luca/steam5/backend test
```

Expected: `BUILD SUCCESSFUL`, only the pre-existing `Steam5ApplicationTests#contextLoads` failure.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/org/steam5/config/QuartzConfig.java backend/src/main/resources/application.yml backend/src/main/resources/application-dev.yml
git commit -m "feat(config): schedule nightly-only hardest-games MV refresh at 00:48 UTC"
```

---

### Task 6: `LeaderboardMvBootstrapConfig` — bootstrap `mv_hardest_games`

**Files:**
- Modify: `backend/src/main/java/org/steam5/config/LeaderboardMvBootstrapConfig.java`
- Modify: `backend/src/test/java/org/steam5/config/LeaderboardMvBootstrapConfigTest.java`

**Interfaces:**
- Consumes: `mv-hardest-games.sql` from Task 1, `LeaderboardType.HARDEST_GAMES`.
- Produces: nothing consumed by later tasks — self-contained.

- [ ] **Step 1: Update the expected `Statement.execute()` counts in `LeaderboardMvBootstrapConfigTest.java` for 5 MVs**

Find:

```java
    @Test
    void bootstrap_neitherExistsNorPopulated_createsViewIndexAndPopulatesForEachOfTheFourMvs() throws Exception {
        stubExistence(false, false, false);

        config.bootstrapLeaderboardMvs(dataSource).run(mock(ApplicationArguments.class));

        // 4 MVs x 3 statements (CREATE MATERIALIZED VIEW + CREATE UNIQUE INDEX CONCURRENTLY +
        // the initial REFRESH) each
        verify(statement, times(12)).execute(any(String.class));
        verify(connection, times(4)).setAutoCommit(true);
        // The initial population is recorded immediately so the freshness header/UI reflects
        // it without waiting for the first scheduled refresh job.
        verify(refreshStateUpsert, times(4)).executeUpdate();
    }
```

Replace with:

```java
    @Test
    void bootstrap_neitherExistsNorPopulated_createsViewIndexAndPopulatesForEachOfTheFiveMvs() throws Exception {
        stubExistence(false, false, false);

        config.bootstrapLeaderboardMvs(dataSource).run(mock(ApplicationArguments.class));

        // 5 MVs x 3 statements (CREATE MATERIALIZED VIEW + CREATE UNIQUE INDEX CONCURRENTLY +
        // the initial REFRESH) each
        verify(statement, times(15)).execute(any(String.class));
        verify(connection, times(5)).setAutoCommit(true);
        // The initial population is recorded immediately so the freshness header/UI reflects
        // it without waiting for the first scheduled refresh job.
        verify(refreshStateUpsert, times(5)).executeUpdate();
    }
```

Find:

```java
    @Test
    void bootstrap_viewExistsAndPopulatedButIndexMissing_createsOnlyTheIndex() throws Exception {
        stubExistence(true, true, false);

        config.bootstrapLeaderboardMvs(dataSource).run(mock(ApplicationArguments.class));

        // 4 MVs x 1 statement (CREATE UNIQUE INDEX CONCURRENTLY only) — already populated, so
        // no initial REFRESH is needed.
        verify(statement, times(4)).execute(any(String.class));
        verify(refreshStateUpsert, never()).executeUpdate();
    }
```

Replace with:

```java
    @Test
    void bootstrap_viewExistsAndPopulatedButIndexMissing_createsOnlyTheIndex() throws Exception {
        stubExistence(true, true, false);

        config.bootstrapLeaderboardMvs(dataSource).run(mock(ApplicationArguments.class));

        // 5 MVs x 1 statement (CREATE UNIQUE INDEX CONCURRENTLY only) — already populated, so
        // no initial REFRESH is needed.
        verify(statement, times(5)).execute(any(String.class));
        verify(refreshStateUpsert, never()).executeUpdate();
    }
```

Find:

```java
    @Test
    void bootstrap_viewExistsButNotPopulated_populatesAndRecordsStateWithoutRecreatingTheView() throws Exception {
        // Covers the exact production/dev symptom this fix addresses: a view (and its index)
        // already exist — created by an earlier bootstrap run, or manually — but nothing has
        // ever refreshed it, so every read fails with "has not been populated" until whichever
        // scheduled job fires next (up to 24h away for the season MV specifically).
        stubExistence(true, false, true);

        config.bootstrapLeaderboardMvs(dataSource).run(mock(ApplicationArguments.class));

        // 4 MVs x 1 statement (REFRESH only) — view+index already exist, just needs populating
        verify(statement, times(4)).execute(any(String.class));
        verify(refreshStateUpsert, times(4)).executeUpdate();
    }
```

Replace with:

```java
    @Test
    void bootstrap_viewExistsButNotPopulated_populatesAndRecordsStateWithoutRecreatingTheView() throws Exception {
        // Covers the exact production/dev symptom this fix addresses: a view (and its index)
        // already exist — created by an earlier bootstrap run, or manually — but nothing has
        // ever refreshed it, so every read fails with "has not been populated" until whichever
        // scheduled job fires next (up to 24h away for the season/hardest-games MVs specifically).
        stubExistence(true, false, true);

        config.bootstrapLeaderboardMvs(dataSource).run(mock(ApplicationArguments.class));

        // 5 MVs x 1 statement (REFRESH only) — view+index already exist, just needs populating
        verify(statement, times(5)).execute(any(String.class));
        verify(refreshStateUpsert, times(5)).executeUpdate();
    }
```

(`bootstrap_bothExistAndPopulated_createsOrRefreshesNothing` and `bootstrap_getConnectionThrows_doesNotPropagate` are unaffected — both assert `never()`/no specific count tied to the number of MVs, so they need no change.)

- [ ] **Step 2: Run it to confirm it fails (wrong counts, since the code still only knows 4 MVs)**

```bash
/Users/nerlich/workspace/luca/steam5/backend/gradlew -p /Users/nerlich/workspace/luca/steam5/backend test --tests "org.steam5.config.LeaderboardMvBootstrapConfigTest"
```

Expected: the 3 updated tests fail with `TooFewActualInvocations` (or similar) against the now-higher expected counts.

- [ ] **Step 3: Add the 5th `MvDefinition` entry**

Find:

```java
    private static final List<MvDefinition> MV_DEFINITIONS = List.of(
            new MvDefinition(LeaderboardType.ALL_TIME, "mv_leaderboard_all_time", "ux_mv_leaderboard_all_time_steam_id", "db/mv-leaderboard-all-time.sql"),
            new MvDefinition(LeaderboardType.MONTHLY, "mv_leaderboard_monthly", "ux_mv_leaderboard_monthly_steam_id", "db/mv-leaderboard-monthly.sql"),
            new MvDefinition(LeaderboardType.WEEKLY, "mv_leaderboard_weekly", "ux_mv_leaderboard_weekly_steam_id", "db/mv-leaderboard-weekly.sql"),
            new MvDefinition(LeaderboardType.SEASON, "mv_leaderboard_season", "ux_mv_leaderboard_season_steam_id", "db/mv-leaderboard-season.sql")
    );
```

Replace with:

```java
    private static final List<MvDefinition> MV_DEFINITIONS = List.of(
            new MvDefinition(LeaderboardType.ALL_TIME, "mv_leaderboard_all_time", "ux_mv_leaderboard_all_time_steam_id", "db/mv-leaderboard-all-time.sql"),
            new MvDefinition(LeaderboardType.MONTHLY, "mv_leaderboard_monthly", "ux_mv_leaderboard_monthly_steam_id", "db/mv-leaderboard-monthly.sql"),
            new MvDefinition(LeaderboardType.WEEKLY, "mv_leaderboard_weekly", "ux_mv_leaderboard_weekly_steam_id", "db/mv-leaderboard-weekly.sql"),
            new MvDefinition(LeaderboardType.SEASON, "mv_leaderboard_season", "ux_mv_leaderboard_season_steam_id", "db/mv-leaderboard-season.sql"),
            new MvDefinition(LeaderboardType.HARDEST_GAMES, "mv_hardest_games", "ux_mv_hardest_games_app_id", "db/mv-hardest-games.sql")
    );
```

Also update the class Javadoc's stale "four leaderboard materialized views" reference:

Find:

```java
/**
 * Creates the four leaderboard materialized views and their unique indexes at startup if
 * they don't already exist, reading the canonical DDL from db/mv-leaderboard-*.sql — the
 * same files an operator would otherwise apply manually via psql. Uses a raw JDBC
 * connection with autocommit, not Hibernate's ddl-auto, because CREATE INDEX CONCURRENTLY
 * cannot run inside a transaction block.
```

Replace with:

```java
/**
 * Creates the leaderboard and hardest-games materialized views and their unique indexes at
 * startup if they don't already exist, reading the canonical DDL from db/mv-leaderboard-*.sql
 * and db/mv-hardest-games.sql — the same files an operator would otherwise apply manually via
 * psql. Uses a raw JDBC connection with autocommit, not Hibernate's ddl-auto, because
 * CREATE INDEX CONCURRENTLY cannot run inside a transaction block.
```

- [ ] **Step 4: Run the test to confirm it passes**

```bash
/Users/nerlich/workspace/luca/steam5/backend/gradlew -p /Users/nerlich/workspace/luca/steam5/backend test --tests "org.steam5.config.LeaderboardMvBootstrapConfigTest"
```

Expected: `BUILD SUCCESSFUL`, all 5 tests passing.

- [ ] **Step 5: Run the full backend suite**

```bash
/Users/nerlich/workspace/luca/steam5/backend/gradlew -p /Users/nerlich/workspace/luca/steam5/backend test
```

Expected: `BUILD SUCCESSFUL`, only the pre-existing `Steam5ApplicationTests#contextLoads` failure.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/org/steam5/config/LeaderboardMvBootstrapConfig.java backend/src/test/java/org/steam5/config/LeaderboardMvBootstrapConfigTest.java
git commit -m "feat(config): bootstrap mv_hardest_games alongside the other leaderboard MVs"
```

---

### Task 7: `StatisticsService#getHardestGames` reads from the MV

**Files:**
- Modify: `backend/src/main/java/org/steam5/service/StatisticsService.java`
- Modify: `backend/src/main/java/org/steam5/repository/GuessRepository.java` (delete now-dead `findHardestGames`/`HardestGameRow`)
- Create: `backend/src/test/java/org/steam5/service/StatisticsServiceTest.java`

**Interfaces:**
- Consumes: `LeaderboardMvRepository.findHardestGames()` from Task 2.
- Produces: `StatisticsService.getHardestGames(int)` keeps its existing signature/return type (`List<HardestGame>`), consumed by Task 8 (`StatisticsController`, unchanged call site).

- [ ] **Step 1: Write `StatisticsServiceTest.java`**

```java
package org.steam5.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.steam5.repository.LeaderboardMvRepository;
import org.steam5.repository.ReviewGamePickRepository;
import org.steam5.repository.ReviewsBucketRepository;
import org.steam5.repository.details.SteamAppDetailRepository;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StatisticsServiceTest {

    private LeaderboardMvRepository leaderboardMvRepository;
    private StatisticsService service;

    @BeforeEach
    void setUp() {
        final SteamAppDetailRepository detailRepository = mock(SteamAppDetailRepository.class);
        final ReviewsBucketRepository reviewsBucketRepository = mock(ReviewsBucketRepository.class);
        final ReviewGamePickRepository reviewGamePickRepository = mock(ReviewGamePickRepository.class);
        final org.steam5.repository.GuessRepository guessRepository = mock(org.steam5.repository.GuessRepository.class);
        final SeasonService seasonService = mock(SeasonService.class);
        final org.springframework.cache.CacheManager cacheManager = mock(org.springframework.cache.CacheManager.class);
        leaderboardMvRepository = mock(LeaderboardMvRepository.class);
        service = new StatisticsService(detailRepository, reviewsBucketRepository, reviewGamePickRepository,
                guessRepository, seasonService, cacheManager, leaderboardMvRepository);
    }

    private LeaderboardMvRepository.HardestGameMvRow row(long appId, String name, double avgScore, long players,
                                                          long tooHigh, long tooLow, long total,
                                                          String mostCommonWrongBucket, Long mostCommonWrongBucketCount,
                                                          String actualBucket, LocalDate latestPickDate) {
        final LeaderboardMvRepository.HardestGameMvRow r = mock(LeaderboardMvRepository.HardestGameMvRow.class);
        when(r.getAppId()).thenReturn(appId);
        when(r.getAppName()).thenReturn(name);
        when(r.getAvgScore()).thenReturn(avgScore);
        when(r.getPlayerCount()).thenReturn(players);
        when(r.getTooHighCount()).thenReturn(tooHigh);
        when(r.getTooLowCount()).thenReturn(tooLow);
        when(r.getTotalGuesses()).thenReturn(total);
        when(r.getMostCommonWrongBucket()).thenReturn(mostCommonWrongBucket);
        when(r.getMostCommonWrongBucketCount()).thenReturn(mostCommonWrongBucketCount);
        when(r.getActualBucket()).thenReturn(actualBucket);
        when(r.getLatestPickDate()).thenReturn(latestPickDate);
        return r;
    }

    @Test
    void getHardestGames_mapsRowsAndComputesDeceptionDirection() {
        final LeaderboardMvRepository.HardestGameMvRow overGuessed = row(1L, "Over Game", 1.5, 10,
                7L, 1L, 10L, "10000+", 4L, "1-100", LocalDate.of(2026, 1, 1));
        final LeaderboardMvRepository.HardestGameMvRow underGuessed = row(2L, "Under Game", 2.0, 8,
                1L, 6L, 8L, "1-100", 3L, "10000+", LocalDate.of(2026, 1, 2));
        when(leaderboardMvRepository.findHardestGames()).thenReturn(List.of(overGuessed, underGuessed));

        final List<StatisticsService.HardestGame> result = service.getHardestGames(10);

        assertEquals(2, result.size());
        assertEquals(1L, result.get(0).appId());
        assertEquals("over", result.get(0).deceptionDirection());
        assertEquals(0.7, result.get(0).deceptionRate());
        assertEquals(2L, result.get(1).appId());
        assertEquals("under", result.get(1).deceptionDirection());
    }

    @Test
    void getHardestGames_respectsLimit() {
        final LeaderboardMvRepository.HardestGameMvRow a = row(1L, "A", 1.0, 5, 0L, 0L, 5L, null, null, "1-100", LocalDate.now());
        final LeaderboardMvRepository.HardestGameMvRow b = row(2L, "B", 1.2, 6, 0L, 0L, 6L, null, null, "1-100", LocalDate.now());
        final LeaderboardMvRepository.HardestGameMvRow c = row(3L, "C", 1.4, 7, 0L, 0L, 7L, null, null, "1-100", LocalDate.now());
        when(leaderboardMvRepository.findHardestGames()).thenReturn(List.of(a, b, c));

        final List<StatisticsService.HardestGame> result = service.getHardestGames(2);

        assertEquals(2, result.size());
        assertEquals(1L, result.get(0).appId());
        assertEquals(2L, result.get(1).appId());
    }

    @Test
    void getHardestGames_noRows_returnsEmptyList() {
        when(leaderboardMvRepository.findHardestGames()).thenReturn(List.of());
        assertEquals(List.of(), service.getHardestGames(10));
    }
}
```

- [ ] **Step 2: Run it to confirm it fails to compile**

```bash
/Users/nerlich/workspace/luca/steam5/backend/gradlew -p /Users/nerlich/workspace/luca/steam5/backend test --tests "org.steam5.service.StatisticsServiceTest"
```

Expected: compile error — `StatisticsService`'s constructor doesn't accept a `LeaderboardMvRepository` yet.

- [ ] **Step 3: Update `StatisticsService.java`**

Find:

```java
import org.steam5.domain.ReviewGamePick;
import org.steam5.domain.Season;
import org.steam5.repository.GuessRepository;
import org.steam5.repository.ReviewGamePickRepository;
import org.steam5.repository.ReviewsBucketRepository;
import org.steam5.repository.details.SteamAppDetailRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StatisticsService {

    private final SteamAppDetailRepository detailRepository;
    private final ReviewsBucketRepository reviewsBucketRepository;
    private final ReviewGamePickRepository reviewGamePickRepository;
    private final GuessRepository guessRepository;
    private final SeasonService seasonService;
    private final CacheManager cacheManager;
```

Replace with:

```java
import org.steam5.domain.ReviewGamePick;
import org.steam5.domain.Season;
import org.steam5.repository.GuessRepository;
import org.steam5.repository.LeaderboardMvRepository;
import org.steam5.repository.ReviewGamePickRepository;
import org.steam5.repository.ReviewsBucketRepository;
import org.steam5.repository.details.SteamAppDetailRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StatisticsService {

    private final SteamAppDetailRepository detailRepository;
    private final ReviewsBucketRepository reviewsBucketRepository;
    private final ReviewGamePickRepository reviewGamePickRepository;
    private final GuessRepository guessRepository;
    private final SeasonService seasonService;
    private final CacheManager cacheManager;
    private final LeaderboardMvRepository leaderboardMvRepository;
```

Find:

```java
    @Cacheable(value = "stats-hourly", key = "'hardest-games-' + #limit", unless = "#result == null")
    public List<HardestGame> getHardestGames(int limit) {
        final List<GuessRepository.HardestGameRow> rows = guessRepository.findHardestGames(limit, 5);
        return rows.stream()
                .map(row -> {
```

Replace with:

```java
    @Cacheable(value = "stats-hourly", key = "'hardest-games-' + #limit", unless = "#result == null")
    public List<HardestGame> getHardestGames(int limit) {
        final List<LeaderboardMvRepository.HardestGameMvRow> rows = leaderboardMvRepository.findHardestGames();
        return rows.stream()
                .limit(limit)
                .map(row -> {
```

Nothing else in the mapping lambda changes — `HardestGameMvRow`'s getters have identical names/types to `GuessRepository.HardestGameRow`'s, so the existing `row.getTooHighCount()`/`row.getAppId()`/etc. calls resolve correctly without further edits.

- [ ] **Step 4: Delete the now-dead `findHardestGames`/`HardestGameRow` from `GuessRepository.java`**

Find:

```java
    interface HardestGameRow {
        Long getAppId();
        String getAppName();
        Double getAvgScore();
        Long getPlayerCount();
        Long getTooHighCount();
        Long getTooLowCount();
        Long getTotalGuesses();
        String getMostCommonWrongBucket();
        Long getMostCommonWrongBucketCount();
        String getActualBucket();
        java.time.LocalDate getLatestPickDate();
    }

    /**
     * Ranks games by difficulty (lowest average points first) with deception metrics.
     * tooHigh/tooLow use the same leading-numeric regex approach as aggregateAllTimeStats.
     * The most-common-wrong bucket is derived via DISTINCT ON per app_id from a CTE of
     * wrong-guess counts per (app_id, selected_bucket).
     */
    @Query(value = """
            WITH wrong_counts AS (
                SELECT app_id, selected_bucket, COUNT(*) AS cnt
                FROM guesses
                WHERE selected_bucket <> actual_bucket
                GROUP BY app_id, selected_bucket
            ),
            top_wrong AS (
                SELECT DISTINCT ON (app_id) app_id, selected_bucket, cnt
                FROM wrong_counts
                ORDER BY app_id, cnt DESC
            )
            SELECT
                g.app_id                                                            AS appId,
                COALESCE(MAX(sai.name), CAST(g.app_id AS TEXT))                     AS appName,
                AVG(g.points)                                                       AS avgScore,
                COUNT(DISTINCT g.steam_id)                                          AS playerCount,
                SUM(CASE WHEN
                    CAST(NULLIF(regexp_replace(g.selected_bucket, '^(\\d+).*', '\\1'), '') AS BIGINT) >
                    CAST(NULLIF(regexp_replace(g.actual_bucket,   '^(\\d+).*', '\\1'), '') AS BIGINT)
                THEN 1 ELSE 0 END)                                                  AS tooHighCount,
                SUM(CASE WHEN
                    CAST(NULLIF(regexp_replace(g.selected_bucket, '^(\\d+).*', '\\1'), '') AS BIGINT) <
                    CAST(NULLIF(regexp_replace(g.actual_bucket,   '^(\\d+).*', '\\1'), '') AS BIGINT)
                THEN 1 ELSE 0 END)                                                  AS tooLowCount,
                COUNT(*)                                                            AS totalGuesses,
                MAX(tw.selected_bucket)                                             AS mostCommonWrongBucket,
                MAX(tw.cnt)                                                         AS mostCommonWrongBucketCount,
                MAX(g.actual_bucket)                                                AS actualBucket,
                MAX(g.game_date)                                                    AS latestPickDate
            FROM guesses g
            LEFT JOIN steam_app_index sai ON sai.app_id = g.app_id
            LEFT JOIN top_wrong tw ON tw.app_id = g.app_id
            GROUP BY g.app_id
            HAVING COUNT(DISTINCT g.steam_id) >= :minPlayers
            ORDER BY AVG(g.points) ASC, COUNT(DISTINCT g.steam_id) DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<HardestGameRow> findHardestGames(@Param("limit") int limit, @Param("minPlayers") int minPlayers);
```

Delete this whole block entirely (both the `HardestGameRow` interface and the `findHardestGames` method/Javadoc). Nothing else in `GuessRepository.java` references either — confirm with:

```bash
grep -n "HardestGameRow\|findHardestGames" backend/src/main/java/org/steam5/repository/GuessRepository.java
```

Expected: no output (both fully removed).

- [ ] **Step 5: Run the test to confirm it passes**

```bash
/Users/nerlich/workspace/luca/steam5/backend/gradlew -p /Users/nerlich/workspace/luca/steam5/backend test --tests "org.steam5.service.StatisticsServiceTest"
```

Expected: `BUILD SUCCESSFUL`, all 3 tests passing.

- [ ] **Step 6: Run the full backend suite**

```bash
/Users/nerlich/workspace/luca/steam5/backend/gradlew -p /Users/nerlich/workspace/luca/steam5/backend test
```

Expected: `BUILD SUCCESSFUL`, only the pre-existing `Steam5ApplicationTests#contextLoads` failure.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/org/steam5/service/StatisticsService.java backend/src/main/java/org/steam5/repository/GuessRepository.java backend/src/test/java/org/steam5/service/StatisticsServiceTest.java
git commit -m "feat(service): read hardest games from mv_hardest_games instead of a live query"
```

---

### Task 8: `StatisticsController#hardestGames` — freshness header

**Files:**
- Modify: `backend/src/main/java/org/steam5/web/StatisticsController.java`
- Create: `backend/src/test/java/org/steam5/web/StatisticsControllerTest.java`

**Interfaces:**
- Consumes: `LeaderboardRefreshStateRepository` (existing, from the earlier freshness-timestamp feature), `LeaderboardType.HARDEST_GAMES` from Task 1.
- Produces: `X-Leaderboard-Refreshed-At` header on `GET /api/stats/game/hardest`, consumed by Task 9's frontend fix.

- [ ] **Step 1: Write `StatisticsControllerTest.java`**

```java
package org.steam5.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.steam5.domain.LeaderboardRefreshState;
import org.steam5.domain.LeaderboardType;
import org.steam5.repository.LeaderboardRefreshStateRepository;
import org.steam5.service.PlayerSpotlightService;
import org.steam5.service.StatisticsService;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StatisticsControllerTest {

    private StatisticsService statisticsService;
    private PlayerSpotlightService playerSpotlightService;
    private LeaderboardRefreshStateRepository refreshStateRepository;
    private StatisticsController controller;

    @BeforeEach
    void setUp() {
        statisticsService = mock(StatisticsService.class);
        playerSpotlightService = mock(PlayerSpotlightService.class);
        refreshStateRepository = mock(LeaderboardRefreshStateRepository.class);
        controller = new StatisticsController(statisticsService, playerSpotlightService, refreshStateRepository);
    }

    @Test
    void hardestGames_setsRefreshedAtHeaderWhenStateExists() {
        List<StatisticsService.HardestGame> canned = List.of(
                new StatisticsService.HardestGame(1L, "Game", 1.5, 10, 0.5, "over", "10000+", 4L, "1-100", java.time.LocalDate.now())
        );
        when(statisticsService.getHardestGames(25)).thenReturn(canned);

        OffsetDateTime refreshedAt = OffsetDateTime.of(2026, 7, 24, 0, 48, 0, 0, ZoneOffset.UTC);
        when(refreshStateRepository.findById(LeaderboardType.HARDEST_GAMES))
                .thenReturn(Optional.of(new LeaderboardRefreshState(LeaderboardType.HARDEST_GAMES, refreshedAt)));

        ResponseEntity<List<StatisticsService.HardestGame>> res = controller.hardestGames(25);

        assertEquals(200, res.getStatusCode().value());
        assertSame(canned, res.getBody());
        assertEquals(refreshedAt.toString(), res.getHeaders().getFirst("X-Leaderboard-Refreshed-At"));
    }

    @Test
    void hardestGames_noRefreshStateYet_omitsHeader() {
        when(statisticsService.getHardestGames(any(Integer.class))).thenReturn(List.of());

        ResponseEntity<List<StatisticsService.HardestGame>> res = controller.hardestGames(25);

        assertNull(res.getHeaders().getFirst("X-Leaderboard-Refreshed-At"));
    }

    @Test
    void hardestGames_clampsLimitTo100() {
        when(statisticsService.getHardestGames(any(Integer.class))).thenReturn(List.of());

        controller.hardestGames(500);

        org.mockito.Mockito.verify(statisticsService).getHardestGames(eq(100));
    }
}
```

- [ ] **Step 2: Run it to confirm it fails to compile**

```bash
/Users/nerlich/workspace/luca/steam5/backend/gradlew -p /Users/nerlich/workspace/luca/steam5/backend test --tests "org.steam5.web.StatisticsControllerTest"
```

Expected: compile error — `StatisticsController`'s constructor doesn't accept a `LeaderboardRefreshStateRepository` yet.

- [ ] **Step 3: Update `StatisticsController.java`**

Find:

```java
import org.steam5.service.PlayerSpotlightService;
import org.steam5.service.StatisticsService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/stats")
@Validated
public class StatisticsController {

    private final StatisticsService statisticsService;
    private final PlayerSpotlightService playerSpotlightService;
```

Replace with:

```java
import org.steam5.domain.LeaderboardType;
import org.steam5.repository.LeaderboardRefreshStateRepository;
import org.steam5.service.PlayerSpotlightService;
import org.steam5.service.StatisticsService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/stats")
@Validated
public class StatisticsController {

    private final StatisticsService statisticsService;
    private final PlayerSpotlightService playerSpotlightService;
    private final LeaderboardRefreshStateRepository refreshStateRepository;
```

Find:

```java
    @GetMapping(value = "/game/hardest", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<StatisticsService.HardestGame>> hardestGames(
            @RequestParam(name = "limit", defaultValue = "25") int limit) {
        final int normalizedLimit = Math.max(1, Math.min(limit, 100));
        final List<StatisticsService.HardestGame> result = statisticsService.getHardestGames(normalizedLimit);
        return ResponseEntity.ok()
                .header("Cache-Control", "public, s-maxage=3600, max-age=600")
                .body(result);
    }
}
```

Replace with:

```java
    @GetMapping(value = "/game/hardest", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<StatisticsService.HardestGame>> hardestGames(
            @RequestParam(name = "limit", defaultValue = "25") int limit) {
        final int normalizedLimit = Math.max(1, Math.min(limit, 100));
        final List<StatisticsService.HardestGame> result = statisticsService.getHardestGames(normalizedLimit);
        final ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .header("Cache-Control", "public, s-maxage=3600, max-age=600");
        refreshStateRepository.findById(LeaderboardType.HARDEST_GAMES)
                .ifPresent(state -> builder.header("X-Leaderboard-Refreshed-At", state.getRefreshedAt().toString()));
        return builder.body(result);
    }
}
```

- [ ] **Step 4: Run the test to confirm it passes**

```bash
/Users/nerlich/workspace/luca/steam5/backend/gradlew -p /Users/nerlich/workspace/luca/steam5/backend test --tests "org.steam5.web.StatisticsControllerTest"
```

Expected: `BUILD SUCCESSFUL`, all 3 tests passing.

- [ ] **Step 5: Run the full backend suite**

```bash
/Users/nerlich/workspace/luca/steam5/backend/gradlew -p /Users/nerlich/workspace/luca/steam5/backend test
```

Expected: `BUILD SUCCESSFUL`, only the pre-existing `Steam5ApplicationTests#contextLoads` failure.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/org/steam5/web/StatisticsController.java backend/src/test/java/org/steam5/web/StatisticsControllerTest.java
git commit -m "feat(web): add X-Leaderboard-Refreshed-At header to GET /api/stats/game/hardest"
```

---

### Task 9: Frontend — forward the header, render "Last updated"

**Files:**
- Modify: `frontend/app/api/stats/game/hardest/route.ts`
- Modify: `frontend/src/components/HardestGamesTable.tsx`

**Interfaces:**
- Consumes: `X-Leaderboard-Refreshed-At` header from Task 8, `formatRefreshedAt` (already exists in `frontend/src/lib/leaderboard.ts` from the earlier freshness feature).
- Produces: nothing consumed by later tasks — this is the last functional task.

No automated test for either file (matches the existing convention — the proxy routes and `LeaderboardTable.tsx` also have no dedicated tests; verified by typecheck + the existing Vitest suite not breaking).

- [ ] **Step 1: Forward the header in the proxy route**

Find:

```typescript
import {NextResponse} from "next/server";

const BACKEND_ORIGIN = process.env.NEXT_PUBLIC_API_DOMAIN || "http://localhost:8080";

export const revalidate = 3600;

export async function GET() {
    try {
        const res = await fetch(`${BACKEND_ORIGIN}/api/stats/game/hardest`, {
            headers: {"accept": "application/json"},
            next: {revalidate, tags: ["stats-hardest-games"]},
        });
        const data = await res.json();
        return NextResponse.json(data, {status: res.status});
    } catch {
        return NextResponse.json({error: "Failed to load hardest games"}, {status: 502});
    }
}
```

Replace with:

```typescript
import {NextResponse} from "next/server";

const BACKEND_ORIGIN = process.env.NEXT_PUBLIC_API_DOMAIN || "http://localhost:8080";

export const revalidate = 3600;

export async function GET() {
    try {
        const res = await fetch(`${BACKEND_ORIGIN}/api/stats/game/hardest`, {
            headers: {"accept": "application/json"},
            next: {revalidate, tags: ["stats-hardest-games"]},
        });
        const data = await res.json();

        // Pass through the leaderboard freshness header (mirrors the achievements/leaderboard
        // routes' pattern) — otherwise NextResponse.json below would silently drop it, and the
        // frontend's "Last updated" line would never have anything to render.
        const refreshedAtHeader = res.headers.get('X-Leaderboard-Refreshed-At');
        const headers: HeadersInit = {};
        if (refreshedAtHeader) {
            headers['X-Leaderboard-Refreshed-At'] = refreshedAtHeader;
        }

        return NextResponse.json(data, {status: res.status, headers});
    } catch {
        return NextResponse.json({error: "Failed to load hardest games"}, {status: 502});
    }
}
```

- [ ] **Step 2: Capture the header and render the "Last updated" line in `HardestGamesTable.tsx`**

Find:

```typescript
"use client";

import "@/styles/components/leaderboard.css";
import Link from "next/link";
import useSWR from "swr";
import {formatDeception, formatMostMissed, HardestGame} from "@/lib/hardestGames";

const ENDPOINT = "/api/stats/game/hardest";

const fetcher = (url: string) => fetch(url, {
    headers: {accept: "application/json"},
    cache: "no-cache",
}).then(r => {
    if (!r.ok) throw new Error(`Failed to load ${url}: ${r.status}`);
    return r.json();
});

export default function HardestGamesTable(props: {
    initialData?: HardestGame[] | null;
    refreshMs?: number;
}) {
    const refreshInterval = props.refreshMs ?? 3600000;

    const {data, error, isLoading} = useSWR<HardestGame[]>(ENDPOINT, fetcher, {
        refreshInterval,
        revalidateOnFocus: true,
        focusThrottleInterval: refreshInterval,
        fallbackData: props.initialData || undefined,
    });
```

Replace with:

```typescript
"use client";

import "@/styles/components/leaderboard.css";
import Link from "next/link";
import useSWR from "swr";
import {formatDeception, formatMostMissed, HardestGame} from "@/lib/hardestGames";
import {formatRefreshedAt} from "@/lib/leaderboard";

const ENDPOINT = "/api/stats/game/hardest";

type HardestGamesFetchResult = { data: HardestGame[]; refreshedAt: string | null };

const fetcher = async (url: string): Promise<HardestGamesFetchResult> => {
    const r = await fetch(url, {
        headers: {accept: "application/json"},
        cache: "no-cache",
    });
    if (!r.ok) throw new Error(`Failed to load ${url}: ${r.status}`);
    const data = await r.json();
    return {data, refreshedAt: r.headers.get('X-Leaderboard-Refreshed-At')};
};

export default function HardestGamesTable(props: {
    initialData?: HardestGame[] | null;
    refreshMs?: number;
}) {
    const refreshInterval = props.refreshMs ?? 3600000;

    const {data: hardestGamesResult, error, isLoading} = useSWR<HardestGamesFetchResult>(ENDPOINT, fetcher, {
        refreshInterval,
        revalidateOnFocus: true,
        focusThrottleInterval: refreshInterval,
        fallbackData: props.initialData ? {data: props.initialData, refreshedAt: null} : undefined,
    });

    const data = hardestGamesResult?.data;
    const refreshedAt = hardestGamesResult?.refreshedAt ?? null;
    const lastUpdatedText = formatRefreshedAt(refreshedAt);
```

Everything below this point that already references `data` (the `error`/`isLoading`/`!data`/`data.length === 0` checks, the `data.map(...)` table body) needs no changes.

Find:

```tsx
                    </tbody>
                </table>
            </div>
        </div>
    );
}
```

Replace with:

```tsx
                    </tbody>
                </table>
            </div>
            {lastUpdatedText && (
                <p className="text-muted leaderboard__last-updated">
                    Last updated: {lastUpdatedText}
                </p>
            )}
        </div>
    );
}
```

- [ ] **Step 3: Typecheck**

```bash
npx --prefix /Users/nerlich/workspace/luca/steam5/frontend tsc --noEmit -p /Users/nerlich/workspace/luca/steam5/frontend/tsconfig.json
```

Expected: no output (no errors).

- [ ] **Step 4: Run the frontend test suite**

```bash
npm --prefix /Users/nerlich/workspace/luca/steam5/frontend test
```

Expected: all tests passing (no dedicated test for these two files, but the run must not break `hardestGames.test.ts` or anything else).

- [ ] **Step 5: Check for the layout.tsx auto-codemod artifact**

```bash
git status --short frontend/app/layout.tsx
```

Expected: no output. If it shows as modified, run `git checkout -- frontend/app/layout.tsx` before committing (see Global Constraints).

- [ ] **Step 6: Commit**

```bash
git add frontend/app/api/stats/game/hardest/route.ts frontend/src/components/HardestGamesTable.tsx
git commit -m "feat(frontend): show last-updated timestamp on the hardest-games table"
```

---

### Task 10: Maintenance script + README updates

**Files:**
- Modify: `backend/src/main/resources/db/leaderboard-mv-maintenance.sql`
- Modify: `README.md`

**Interfaces:**
- Consumes: everything from Tasks 1-9.
- Produces: nothing — documentation only.

- [ ] **Step 1: Add `mv_hardest_games` to the maintenance script's DROP and SELECT sections**

Find:

```sql
DROP MATERIALIZED VIEW IF EXISTS mv_leaderboard_all_time, mv_leaderboard_monthly, mv_leaderboard_weekly, mv_leaderboard_season CASCADE;
```

Replace with:

```sql
DROP MATERIALIZED VIEW IF EXISTS mv_leaderboard_all_time, mv_leaderboard_monthly, mv_leaderboard_weekly, mv_leaderboard_season, mv_hardest_games CASCADE;
```

Find:

```sql
SELECT * FROM mv_leaderboard_season ORDER BY total_points DESC;

-- =============================================================================
-- C) Check whether each view has ever been populated / when it was last refreshed
-- =============================================================================
```

Replace with:

```sql
SELECT * FROM mv_leaderboard_season ORDER BY total_points DESC;

SELECT * FROM mv_hardest_games ORDER BY avg_score ASC, player_count DESC;

-- =============================================================================
-- C) Check whether each view has ever been populated / when it was last refreshed
-- =============================================================================
```

Find:

```sql
SELECT matviewname, ispopulated FROM pg_matviews WHERE matviewname LIKE 'mv_leaderboard_%';
```

Replace with:

```sql
SELECT matviewname, ispopulated FROM pg_matviews WHERE matviewname LIKE 'mv_leaderboard_%' OR matviewname = 'mv_hardest_games';
```

- [ ] **Step 2: Update the README's "Query Performance Notes" section**

Find:

```text
- Leaderboard reads (`/api/leaderboard/all`, `/monthly`, `/weekly?floating=true`, `/season`) are served
```

Find the paragraph that begins with that line and locate its end (the bullet list item spanning the MV description, rollout order, and freshness header explanation), then add a new bullet immediately after the whole existing leaderboard-MV bullet (i.e., after the closing of that entire nested bullet block, at the same top-level `-` indentation), containing:

```text
- `mv_hardest_games` backs `GET /api/stats/game/hardest` the same way — see
  `backend/src/main/resources/db/mv-hardest-games.sql`. Refreshed once daily only (00:48 UTC,
  `jobs.leaderboard-refresh-hardest-games.enabled`, default `true`) — no intraday trigger, since
  game-difficulty rankings change slowly. Also auto-bootstrapped, drop-listed for `pg_restore
  --clean` (see `leaderboard-mv-maintenance.sql`), and exposes the same `X-Leaderboard-Refreshed-At`
  header/"Last updated" UI as the other four.
```

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/resources/db/leaderboard-mv-maintenance.sql README.md
git commit -m "docs: document mv_hardest_games in the maintenance script and README"
```
