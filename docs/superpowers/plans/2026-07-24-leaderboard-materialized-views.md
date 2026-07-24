# Leaderboard Materialized Views Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move the all-time, monthly (rolling 30-day), weekly (rolling 7-day), and season leaderboards off live per-request aggregation onto four Postgres materialized views, refreshed by dedicated Quartz jobs, while keeping the existing API contract, the `today` live path, and the Caffeine `leaderboard-static` last-mile cache exactly as they are.

**Architecture:** Four new materialized views (`mv_leaderboard_all_time`, `mv_leaderboard_monthly`, `mv_leaderboard_weekly`, `mv_leaderboard_season`) reproduce `GuessRepository#aggregateAllTimeStats()`'s aggregation exactly (same regex-based too-high/too-low bucket comparison, same `GROUP BY steam_id`), each `LEFT JOIN`ed against `users` for profile fields and scoped to its own date window. A new `LeaderboardMvRepository` reads them and also issues their `REFRESH MATERIALIZED VIEW [CONCURRENTLY]` statements; a new `LeaderboardRefreshService` picks concurrent vs. non-concurrent refresh based on `pg_matviews.ispopulated`; a single parameterized `LeaderboardRefreshJob` (one `JobDetail` bean per type) refreshes-then-evicts on a nightly cron (staggered after `seasons-finalizer`) plus a 10-minute intraday tick for the three non-season types. `LeaderboardService` grows MV-backed builder methods that overlay each row's live streak (`findDistinctDatesUpToForUsers` + `StreakCalculator`, unchanged) onto the pre-aggregated MV columns; `LeaderboardController` routes to them while preserving every existing `@Cacheable`/manual-cache annotation, and keeps the non-floating `/weekly` variant on its current live `findAllBetween` path, since its window (previous full Mon-Sun week) doesn't match any MV's rolling/current-window definition.

**Tech Stack:** Spring Boot 3 / Java 21 backend (Gradle), Spring Data JPA native queries, Quartz, Caffeine (via `CacheConfig`/`DomainCacheEvictor`), PostgreSQL materialized views, JUnit 5 + Mockito.

## Global Constraints

- **Test/build commands** (this repo's `cd backend && ...` convention hits a permission wall in some sandboxed sessions — the `-p` form below is confirmed to work without `cd`):
  - Fast loop: `/Users/nerlich/workspace/luca/steam5/backend/gradlew -p /Users/nerlich/workspace/luca/steam5/backend test --tests "org.steam5.service.LeaderboardServiceTest"` (swap the test class per task).
  - Full regression: `/Users/nerlich/workspace/luca/steam5/backend/gradlew -p /Users/nerlich/workspace/luca/steam5/backend test`.
  - Compile-only check: `/Users/nerlich/workspace/luca/steam5/backend/gradlew -p /Users/nerlich/workspace/luca/steam5/backend compileJava compileTestJava`.
  - None of the Java-side tasks below (Tasks 2-8) need a live Postgres — every repository dependency is Mockito-mocked in tests. Only Task 1's verification step touches a real database.
- **DB connection for manual verification** (Task 1 only), from `application.yml` defaults: host `localhost`, port `5432`, database `steam5_db`, user `steam5_user`, password `steam5_password`. Adjust if your local setup differs.
- **MV names (exact):** `mv_leaderboard_all_time`, `mv_leaderboard_monthly`, `mv_leaderboard_weekly`, `mv_leaderboard_season`.
- **MV column names (exact, identical shape across all four):** `steam_id`, `total_points`, `rounds`, `hits`, `flops`, `too_high`, `too_low`, `avg_points`, `persona_name`, `avatar_full`, `blurdata_avatar_full`, `profile_url`.
- **Unique index names (exact):** `ux_mv_leaderboard_all_time_steam_id`, `ux_mv_leaderboard_monthly_steam_id`, `ux_mv_leaderboard_weekly_steam_id`, `ux_mv_leaderboard_season_steam_id` — each `CREATE UNIQUE INDEX CONCURRENTLY ... (steam_id)`, required by `REFRESH MATERIALIZED VIEW CONCURRENTLY`.
- **LEFT JOIN, not INNER JOIN, against `users`:** a guess from a `steam_id` with no matching `users` row must still appear in the MV (profile columns come back `NULL`), matching `LeaderboardService`'s existing null-safe fallback (`personaName` → `steamId`, avatar/profile fields → `null`). An `INNER JOIN` would silently drop such players and change leaderboard membership — do not use it.
- **Postgres functional-dependency `GROUP BY` rule used in every MV:** `GROUP BY g.steam_id, u.steam_id` (both columns, even though the `LEFT JOIN` means `u.steam_id` can be `NULL`) licenses referencing `u.persona_name`/`u.avatar_full`/`u.blurdata_avatar_full`/`u.profile_url` in the `SELECT` list without aggregating them, because `u.steam_id` is the full primary key of `users`. `SELECT`'s own `steam_id` column must be `g.steam_id` (never null), not `u.steam_id`.
- **Weekly has two variants — only one is MV-backed:** `floating=true` (rolling 7 days ending today) is backed by `mv_leaderboard_weekly`. `floating=false` (default; previous full Monday-Sunday week) is **not** MV-backed — its window doesn't roll with `CURRENT_DATE` the same way — and keeps using the existing live `GuessRepository#findAllBetween` + `LeaderboardService#buildLeaderboard` path, unchanged.
- **`CREATE INDEX CONCURRENTLY` cannot run inside a transaction block** — each MV's `.sql` script must be applied as separate top-level statements (no `BEGIN`/`COMMIT` wrapping), same as the existing `idx_guesses_game_date` precedent in `Guess.java`. `REFRESH MATERIALIZED VIEW [CONCURRENTLY]`, by contrast, has no such restriction and runs fine inside a normal Spring `@Transactional` method.
- **`REFRESH MATERIALIZED VIEW CONCURRENTLY` requires prior population:** every MV is created `WITH NO DATA`. `LeaderboardRefreshService` checks `pg_matviews.ispopulated` and automatically falls back to a plain (non-concurrent) `REFRESH` the first time; ops may also do this manually. Querying an unpopulated MV raises a Postgres error — do not serve the MV-backed read path before the first population.
- **Rollout order** (documented in README, not enforced by code): 1) apply the four DDL scripts, 2) populate each view (manual `REFRESH` or let the job self-heal on first run), 3) enable the corresponding `jobs.leaderboard-refresh-*.enabled` flags, 4) only then is it safe to deploy/serve the MV-backed read path in production.
- **New repository:** `org.steam5.repository.LeaderboardMvRepository`, extends `org.springframework.data.repository.Repository<Guess, Long>` (plain marker — every method is a hand-written native `@Query`; `Guess` is reused only to satisfy the generic bound). Nested projection interface `LeaderboardMvRow` with getters `getSteamId()/getTotalPoints()/getRounds()/getHits()/getFlops()/getTooHigh()/getTooLow()/getAvgPoints()/getPersonaName()/getAvatarFull()/getBlurdataAvatarFull()/getProfileUrl()`. Read methods `findAllTime()/findMonthly()/findWeekly()/findSeason()` return `List<LeaderboardMvRow>` ordered by `total_points DESC` (the MV query does the ordering — no re-sort needed in Java). Refresh methods (added in Task 5): `refreshAllTimeConcurrently()/refreshAllTimeFull()/refreshMonthlyConcurrently()/refreshMonthlyFull()/refreshWeeklyConcurrently()/refreshWeeklyFull()/refreshSeasonConcurrently()/refreshSeasonFull()`, plus `Boolean isPopulated(String viewName)`.
- **New service:** `org.steam5.service.LeaderboardRefreshService` with `refreshAllTime()/refreshMonthly()/refreshWeekly()/refreshSeason()` — each checks `isPopulated`, then calls the matching concurrent-or-full pair.
- **`LeaderboardService` gains a third constructor dependency** (`LeaderboardMvRepository`, field order: `guessRepository`, `userRepository`, `leaderboardMvRepository` — Lombok `@RequiredArgsConstructor` generates the constructor in this field order). New methods: `buildMonthlyLeaderboard(LocalDate today)`, `buildWeeklyLeaderboard(LocalDate today)`, `buildSeasonLeaderboard(LocalDate asOfDate)`. `buildAllTimeLeaderboard(LocalDate today)` keeps its existing signature but is reimplemented on top of the MV. `buildLeaderboard(...)` (the `today` path) and its private helpers (`buildEntry`, `getLeaderEntry`) are **not modified at all**.
- **Cache name (existing, unchanged):** `leaderboard-static` (10-minute Caffeine TTL, declared in `CacheConfig`). Do not modify `CacheConfig`.
- **`DomainCacheEvictor` gains:** constant `LEADERBOARD_STATIC = "leaderboard-static"` and method `evictLeaderboardStatic()`.
- **New job:** `org.steam5.job.LeaderboardRefreshJob`, nested `enum LeaderboardType { ALL_TIME, MONTHLY, WEEKLY, SEASON }`, driven by the `"type"` `JobDataMap` entry. Four `@Bean` `JobDetail` methods named `LeaderboardRefreshJob_AllTime`, `LeaderboardRefreshJob_Monthly`, `LeaderboardRefreshJob_Weekly`, `LeaderboardRefreshJob_Season`.
- **Config property prefixes (exact):** `jobs.leaderboard-refresh-all-time.enabled`, `jobs.leaderboard-refresh-monthly.enabled`, `jobs.leaderboard-refresh-weekly.enabled`, `jobs.leaderboard-refresh-season.enabled`.
- **Cron schedule (UTC), staggered after `seasons-finalizer` (00:25):** all-time 00:40, monthly 00:42, weekly 00:44, season 00:46. All-time/monthly/weekly additionally get a 10-minute-interval intraday trigger (matching the `leaderboard-static` TTL), gated by the same `enabled` flag; season gets no intraday trigger — its correctness depends on season rollover timing, not intraday freshness.
- **Dead code removal:** `GuessRepository#aggregateAllTimeStats()` becomes unused once `LeaderboardService` is repointed at the MV (Task 3) and is deleted in that same task; `aggregateAllTimeStatsHavingMinRounds()` (used by `PlayerSpotlightService`, unrelated) stays, with its Javadoc `{@link #aggregateAllTimeStats()}` reference rewritten so it doesn't point at deleted code.
- **`JobsConfig.java` is intentionally left unmodified:** the spec allows a nested config class there "if per-type tunables are needed." The four refresh jobs take no parameters beyond their fixed view name, so there's nothing to tune — the `@ConditionalOnProperty(prefix = "jobs.leaderboard-refresh-<type>", name = "enabled", ...)` flags in `QuartzConfig` (Task 7) are sufficient, matching how `seasons-finalizer`/`seasons-backfill`/`player-spotlight`/`review-game-state` also gate purely off their own `enabled` flag with no `JobsConfig` entry (only jobs with actual per-run batch limits, like `blurhash`, need one).
- **`SeasonService` hook — intentionally skipped:** the spec allows an optional hook in `ensureSeasonForDate`/`finalizeSeason` to force a season-MV refresh at rollover. Not implemented here — the season job's 00:46 UTC cron already runs after `seasons-finalizer` (00:25), so the extra coupling isn't justified. Documented as a deliberate no-op in Task 8's README update.

---

### Task 1: Materialized view DDL scripts

**Files:**
- Create: `backend/src/main/resources/db/mv-leaderboard-all-time.sql`
- Create: `backend/src/main/resources/db/mv-leaderboard-monthly.sql`
- Create: `backend/src/main/resources/db/mv-leaderboard-weekly.sql`
- Create: `backend/src/main/resources/db/mv-leaderboard-season.sql`
- Modify: `README.md` (Query Performance Notes)

**Interfaces:**
- Produces: the four MV names/columns/indexes listed in Global Constraints, which every later task depends on by name.
- Consumes: nothing (pure SQL, no Java).

This task has no automated test — these scripts are manually-applied DDL (like the existing `idx_guesses_game_date` precedent), never run by Hibernate `ddl-auto`. Verification is a manual `psql` apply against your local dev Postgres plus a cross-check against an ad hoc aggregate query.

- [ ] **Step 1: Create `mv-leaderboard-all-time.sql`**

```sql
-- Materialized view backing the all-time leaderboard read path
-- (LeaderboardService#buildAllTimeLeaderboard / GET /api/leaderboard/all).
--
-- Reproduces GuessRepository#aggregateAllTimeStats() exactly: same GROUP BY steam_id,
-- same too-high/too-low leading-numeric-bucket regex comparison. LEFT JOINs `users` so a
-- guess from a steam_id with no `users` row still appears (profile columns come back NULL,
-- matching LeaderboardService's existing null-safe fallback) instead of being silently
-- dropped by an INNER JOIN.
--
-- NOT managed by Hibernate ddl-auto — apply manually, same convention as
-- idx_guesses_game_date (see README "Query Performance Notes"). Refreshed by
-- LeaderboardRefreshJob (org.steam5.job) via REFRESH MATERIALIZED VIEW CONCURRENTLY, which
-- requires the unique index below AND that the view already be populated at least once —
-- LeaderboardRefreshService detects an unpopulated view (pg_matviews.ispopulated) and falls
-- back to a plain REFRESH automatically the first time it runs.
--
-- Freshness is bounded by refresh cadence (see jobs.leaderboard-refresh-all-time.enabled /
-- QuartzConfig), not by the leaderboard-static Caffeine TTL.
CREATE MATERIALIZED VIEW mv_leaderboard_all_time AS
SELECT
    g.steam_id                                                          AS steam_id,
    SUM(g.points)                                                       AS total_points,
    COUNT(*)                                                            AS rounds,
    SUM(CASE WHEN g.selected_bucket = g.actual_bucket THEN 1 ELSE 0 END) AS hits,
    SUM(CASE WHEN g.points = 0 THEN 1 ELSE 0 END)                       AS flops,
    SUM(CASE WHEN
        CAST(NULLIF(regexp_replace(g.selected_bucket, '^(\d+).*', '\1'), '') AS BIGINT) >
        CAST(NULLIF(regexp_replace(g.actual_bucket,   '^(\d+).*', '\1'), '') AS BIGINT)
    THEN 1 ELSE 0 END)                                                  AS too_high,
    SUM(CASE WHEN
        CAST(NULLIF(regexp_replace(g.selected_bucket, '^(\d+).*', '\1'), '') AS BIGINT) <
        CAST(NULLIF(regexp_replace(g.actual_bucket,   '^(\d+).*', '\1'), '') AS BIGINT)
    THEN 1 ELSE 0 END)                                                  AS too_low,
    AVG(g.points)                                                       AS avg_points,
    u.persona_name                                                      AS persona_name,
    u.avatar_full                                                       AS avatar_full,
    u.blurdata_avatar_full                                              AS blurdata_avatar_full,
    u.profile_url                                                       AS profile_url
FROM guesses g
LEFT JOIN users u ON u.steam_id = g.steam_id
GROUP BY g.steam_id, u.steam_id
ORDER BY SUM(g.points) DESC
WITH NO DATA;

-- Required for REFRESH MATERIALIZED VIEW CONCURRENTLY. CREATE INDEX CONCURRENTLY cannot run
-- inside a transaction block — run this as its own statement (e.g. a single psql command),
-- not wrapped in BEGIN/COMMIT.
CREATE UNIQUE INDEX CONCURRENTLY ux_mv_leaderboard_all_time_steam_id
    ON mv_leaderboard_all_time (steam_id);
```

- [ ] **Step 2: Create `mv-leaderboard-monthly.sql`**

```sql
-- Materialized view backing the rolling 30-day ("monthly") leaderboard read path
-- (LeaderboardService#buildMonthlyLeaderboard / GET /api/leaderboard/monthly).
--
-- Same aggregation as mv-leaderboard-all-time.sql (see that file's header for the
-- too-high/too-low regex explanation), scoped to the 30 days ending on the database's
-- CURRENT_DATE. The window is re-derived from CURRENT_DATE on every refresh, so it rolls
-- forward automatically — no stored start/end dates to maintain. CURRENT_DATE reflects the
-- database session's timezone; since the app computes "today" as GameDate.todayUtc(), the
-- database (or at least this session) must also be UTC, or the MV's day boundary will drift
-- from the app's.
--
-- NOT managed by Hibernate ddl-auto — apply manually (see README "Query Performance Notes").
-- Refreshed by LeaderboardRefreshJob via REFRESH MATERIALIZED VIEW CONCURRENTLY (requires the
-- unique index below and prior population — see mv-leaderboard-all-time.sql's header).
--
-- Freshness is bounded by refresh cadence (jobs.leaderboard-refresh-monthly.enabled), which
-- also means the 30-day window itself only advances at refresh time, not continuously.
CREATE MATERIALIZED VIEW mv_leaderboard_monthly AS
SELECT
    g.steam_id                                                          AS steam_id,
    SUM(g.points)                                                       AS total_points,
    COUNT(*)                                                            AS rounds,
    SUM(CASE WHEN g.selected_bucket = g.actual_bucket THEN 1 ELSE 0 END) AS hits,
    SUM(CASE WHEN g.points = 0 THEN 1 ELSE 0 END)                       AS flops,
    SUM(CASE WHEN
        CAST(NULLIF(regexp_replace(g.selected_bucket, '^(\d+).*', '\1'), '') AS BIGINT) >
        CAST(NULLIF(regexp_replace(g.actual_bucket,   '^(\d+).*', '\1'), '') AS BIGINT)
    THEN 1 ELSE 0 END)                                                  AS too_high,
    SUM(CASE WHEN
        CAST(NULLIF(regexp_replace(g.selected_bucket, '^(\d+).*', '\1'), '') AS BIGINT) <
        CAST(NULLIF(regexp_replace(g.actual_bucket,   '^(\d+).*', '\1'), '') AS BIGINT)
    THEN 1 ELSE 0 END)                                                  AS too_low,
    AVG(g.points)                                                       AS avg_points,
    u.persona_name                                                      AS persona_name,
    u.avatar_full                                                       AS avatar_full,
    u.blurdata_avatar_full                                              AS blurdata_avatar_full,
    u.profile_url                                                       AS profile_url
FROM guesses g
LEFT JOIN users u ON u.steam_id = g.steam_id
WHERE g.game_date BETWEEN CURRENT_DATE - 29 AND CURRENT_DATE
GROUP BY g.steam_id, u.steam_id
ORDER BY SUM(g.points) DESC
WITH NO DATA;

-- Required for REFRESH MATERIALIZED VIEW CONCURRENTLY. CREATE INDEX CONCURRENTLY cannot run
-- inside a transaction block — run this as its own statement, not wrapped in BEGIN/COMMIT.
CREATE UNIQUE INDEX CONCURRENTLY ux_mv_leaderboard_monthly_steam_id
    ON mv_leaderboard_monthly (steam_id);
```

- [ ] **Step 3: Create `mv-leaderboard-weekly.sql`**

```sql
-- Materialized view backing the rolling 7-day ("floating weekly") leaderboard read path
-- (LeaderboardService#buildWeeklyLeaderboard / GET /api/leaderboard/weekly?floating=true).
--
-- Same aggregation as mv-leaderboard-all-time.sql (see that file's header for the
-- too-high/too-low regex explanation), scoped to the 7 days ending on the database's
-- CURRENT_DATE. See mv-leaderboard-monthly.sql's header for the CURRENT_DATE/timezone caveat.
--
-- Does NOT back the non-floating `/weekly` variant (the previous full Monday-Sunday week) —
-- that window doesn't roll with CURRENT_DATE the same way, and LeaderboardController#weekly
-- keeps computing it live via GuessRepository#findAllBetween.
--
-- NOT managed by Hibernate ddl-auto — apply manually (see README "Query Performance Notes").
-- Refreshed by LeaderboardRefreshJob via REFRESH MATERIALIZED VIEW CONCURRENTLY (requires the
-- unique index below and prior population — see mv-leaderboard-all-time.sql's header).
CREATE MATERIALIZED VIEW mv_leaderboard_weekly AS
SELECT
    g.steam_id                                                          AS steam_id,
    SUM(g.points)                                                       AS total_points,
    COUNT(*)                                                            AS rounds,
    SUM(CASE WHEN g.selected_bucket = g.actual_bucket THEN 1 ELSE 0 END) AS hits,
    SUM(CASE WHEN g.points = 0 THEN 1 ELSE 0 END)                       AS flops,
    SUM(CASE WHEN
        CAST(NULLIF(regexp_replace(g.selected_bucket, '^(\d+).*', '\1'), '') AS BIGINT) >
        CAST(NULLIF(regexp_replace(g.actual_bucket,   '^(\d+).*', '\1'), '') AS BIGINT)
    THEN 1 ELSE 0 END)                                                  AS too_high,
    SUM(CASE WHEN
        CAST(NULLIF(regexp_replace(g.selected_bucket, '^(\d+).*', '\1'), '') AS BIGINT) <
        CAST(NULLIF(regexp_replace(g.actual_bucket,   '^(\d+).*', '\1'), '') AS BIGINT)
    THEN 1 ELSE 0 END)                                                  AS too_low,
    AVG(g.points)                                                       AS avg_points,
    u.persona_name                                                      AS persona_name,
    u.avatar_full                                                       AS avatar_full,
    u.blurdata_avatar_full                                              AS blurdata_avatar_full,
    u.profile_url                                                       AS profile_url
FROM guesses g
LEFT JOIN users u ON u.steam_id = g.steam_id
WHERE g.game_date BETWEEN CURRENT_DATE - 6 AND CURRENT_DATE
GROUP BY g.steam_id, u.steam_id
ORDER BY SUM(g.points) DESC
WITH NO DATA;

-- Required for REFRESH MATERIALIZED VIEW CONCURRENTLY. CREATE INDEX CONCURRENTLY cannot run
-- inside a transaction block — run this as its own statement, not wrapped in BEGIN/COMMIT.
CREATE UNIQUE INDEX CONCURRENTLY ux_mv_leaderboard_weekly_steam_id
    ON mv_leaderboard_weekly (steam_id);
```

- [ ] **Step 4: Create `mv-leaderboard-season.sql`**

```sql
-- Materialized view backing the current-season leaderboard read path
-- (LeaderboardService#buildSeasonLeaderboard / GET /api/leaderboard/season).
--
-- Same aggregation as mv-leaderboard-all-time.sql (see that file's header for the
-- too-high/too-low regex explanation), scoped to whichever `seasons` row's
-- [start_date, end_date] currently contains the database's CURRENT_DATE (mirrors
-- SeasonService#findSeasonContaining). If no season row covers CURRENT_DATE (e.g. before
-- the first season is created), the view is empty rather than erroring.
--
-- The refresh job must run AFTER same-day season rollover/finalization (SeasonFinalizerJob,
-- 00:25 UTC) so the season boundary is settled before this view re-derives its window —
-- see QuartzConfig's 00:46 UTC trigger for this type, which intentionally has no additional
-- intraday trigger (unlike all-time/monthly/weekly).
--
-- NOT managed by Hibernate ddl-auto — apply manually (see README "Query Performance Notes").
-- Refreshed by LeaderboardRefreshJob via REFRESH MATERIALIZED VIEW CONCURRENTLY (requires the
-- unique index below and prior population — see mv-leaderboard-all-time.sql's header).
CREATE MATERIALIZED VIEW mv_leaderboard_season AS
WITH current_season AS (
    SELECT start_date, end_date
    FROM seasons
    WHERE start_date <= CURRENT_DATE AND end_date >= CURRENT_DATE
    ORDER BY season_number DESC
    LIMIT 1
)
SELECT
    g.steam_id                                                          AS steam_id,
    SUM(g.points)                                                       AS total_points,
    COUNT(*)                                                            AS rounds,
    SUM(CASE WHEN g.selected_bucket = g.actual_bucket THEN 1 ELSE 0 END) AS hits,
    SUM(CASE WHEN g.points = 0 THEN 1 ELSE 0 END)                       AS flops,
    SUM(CASE WHEN
        CAST(NULLIF(regexp_replace(g.selected_bucket, '^(\d+).*', '\1'), '') AS BIGINT) >
        CAST(NULLIF(regexp_replace(g.actual_bucket,   '^(\d+).*', '\1'), '') AS BIGINT)
    THEN 1 ELSE 0 END)                                                  AS too_high,
    SUM(CASE WHEN
        CAST(NULLIF(regexp_replace(g.selected_bucket, '^(\d+).*', '\1'), '') AS BIGINT) <
        CAST(NULLIF(regexp_replace(g.actual_bucket,   '^(\d+).*', '\1'), '') AS BIGINT)
    THEN 1 ELSE 0 END)                                                  AS too_low,
    AVG(g.points)                                                       AS avg_points,
    u.persona_name                                                      AS persona_name,
    u.avatar_full                                                       AS avatar_full,
    u.blurdata_avatar_full                                              AS blurdata_avatar_full,
    u.profile_url                                                       AS profile_url
FROM guesses g
JOIN current_season cs ON g.game_date BETWEEN cs.start_date AND cs.end_date
LEFT JOIN users u ON u.steam_id = g.steam_id
GROUP BY g.steam_id, u.steam_id
ORDER BY SUM(g.points) DESC
WITH NO DATA;

-- Required for REFRESH MATERIALIZED VIEW CONCURRENTLY. CREATE INDEX CONCURRENTLY cannot run
-- inside a transaction block — run this as its own statement, not wrapped in BEGIN/COMMIT.
CREATE UNIQUE INDEX CONCURRENTLY ux_mv_leaderboard_season_steam_id
    ON mv_leaderboard_season (steam_id);
```

- [ ] **Step 5: Apply all four scripts against your local dev Postgres and verify**

```bash
PGPASSWORD=steam5_password psql -h localhost -U steam5_user -d steam5_db -f backend/src/main/resources/db/mv-leaderboard-all-time.sql
PGPASSWORD=steam5_password psql -h localhost -U steam5_user -d steam5_db -f backend/src/main/resources/db/mv-leaderboard-monthly.sql
PGPASSWORD=steam5_password psql -h localhost -U steam5_user -d steam5_db -f backend/src/main/resources/db/mv-leaderboard-weekly.sql
PGPASSWORD=steam5_password psql -h localhost -U steam5_user -d steam5_db -f backend/src/main/resources/db/mv-leaderboard-season.sql
```

Expected output per file: `CREATE MATERIALIZED VIEW` then `CREATE INDEX`.

- [ ] **Step 6: Populate and cross-check the all-time view against a live aggregate**

```bash
PGPASSWORD=steam5_password psql -h localhost -U steam5_user -d steam5_db -c "REFRESH MATERIALIZED VIEW mv_leaderboard_all_time;"
PGPASSWORD=steam5_password psql -h localhost -U steam5_user -d steam5_db -c "SELECT steam_id, total_points, rounds FROM mv_leaderboard_all_time ORDER BY total_points DESC LIMIT 5;"
PGPASSWORD=steam5_password psql -h localhost -U steam5_user -d steam5_db -c "SELECT steam_id, SUM(points) AS total_points, COUNT(*) AS rounds FROM guesses GROUP BY steam_id ORDER BY total_points DESC LIMIT 5;"
```

Expected: the two result sets match row-for-row (same top 5 `steam_id`s, same `total_points`/`rounds`). If your dev DB has no `guesses` rows yet, both queries just return zero rows — that's fine, it still confirms the MV applies and queries cleanly.

- [ ] **Step 7: Add the manual-application note to README.md**

Find this line in the "Query Performance Notes" section:

```
- `guesses` date-range queries (for example `findAllBetween`, `findSeasonStats`, `findSeasonDates`) rely on `idx_guesses_game_date`.
```

Add this new bullet immediately after it:

```
- Leaderboard reads (`/api/leaderboard/all`, `/monthly`, `/weekly?floating=true`, `/season`) are backed by
  materialized views (`mv_leaderboard_all_time`, `mv_leaderboard_monthly`, `mv_leaderboard_weekly`,
  `mv_leaderboard_season` — see `backend/src/main/resources/db/mv-leaderboard-*.sql`). Like
  `idx_guesses_game_date`, these are **not** managed by Hibernate `ddl-auto` and must be applied manually
  against every environment (including each new dev DB). See the expanded write-up further down this
  section for the full rollout order and refresh-job configuration.
```

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/resources/db/mv-leaderboard-all-time.sql backend/src/main/resources/db/mv-leaderboard-monthly.sql backend/src/main/resources/db/mv-leaderboard-weekly.sql backend/src/main/resources/db/mv-leaderboard-season.sql README.md
git commit -m "feat(db): add leaderboard materialized view DDL scripts"
```

---

### Task 2: `LeaderboardMvRepository` read queries

**Files:**
- Create: `backend/src/main/java/org/steam5/repository/LeaderboardMvRepository.java`

**Interfaces:**
- Consumes: MV names/columns from Task 1 (Global Constraints).
- Produces: `LeaderboardMvRepository.LeaderboardMvRow` projection and `findAllTime()/findMonthly()/findWeekly()/findSeason()`, which Task 3 depends on.

No dedicated unit test for this file — it's a pure interface with hand-written native `@Query` methods and no branching logic to unit test (matches the existing convention: none of `GuessRepository`'s native queries have direct tests either; correctness is exercised indirectly through the service tests in Task 3, plus the manual `psql` verification in Task 1). Verification here is a compile check.

- [ ] **Step 1: Create `LeaderboardMvRepository.java`**

```java
package org.steam5.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.steam5.domain.Guess;

import java.util.List;

/**
 * Read access to the leaderboard materialized views (see
 * backend/src/main/resources/db/mv-leaderboard-*.sql). These views are not JPA-managed
 * entities, so this repository extends the plain {@link Repository} marker — every method
 * is a hand-written native query. {@link Guess} is reused only to satisfy the generic bound;
 * none of these methods touch the {@code guesses} table directly.
 */
public interface LeaderboardMvRepository extends Repository<Guess, Long> {

    interface LeaderboardMvRow {
        String getSteamId();
        Long getTotalPoints();
        Long getRounds();
        Long getHits();
        Long getFlops();
        Long getTooHigh();
        Long getTooLow();
        Double getAvgPoints();
        String getPersonaName();
        String getAvatarFull();
        String getBlurdataAvatarFull();
        String getProfileUrl();
    }

    @Query(value = "SELECT steam_id AS steamId, total_points AS totalPoints, rounds, hits, flops, " +
            "too_high AS tooHigh, too_low AS tooLow, avg_points AS avgPoints, persona_name AS personaName, " +
            "avatar_full AS avatarFull, blurdata_avatar_full AS blurdataAvatarFull, profile_url AS profileUrl " +
            "FROM mv_leaderboard_all_time ORDER BY total_points DESC", nativeQuery = true)
    List<LeaderboardMvRow> findAllTime();

    @Query(value = "SELECT steam_id AS steamId, total_points AS totalPoints, rounds, hits, flops, " +
            "too_high AS tooHigh, too_low AS tooLow, avg_points AS avgPoints, persona_name AS personaName, " +
            "avatar_full AS avatarFull, blurdata_avatar_full AS blurdataAvatarFull, profile_url AS profileUrl " +
            "FROM mv_leaderboard_monthly ORDER BY total_points DESC", nativeQuery = true)
    List<LeaderboardMvRow> findMonthly();

    @Query(value = "SELECT steam_id AS steamId, total_points AS totalPoints, rounds, hits, flops, " +
            "too_high AS tooHigh, too_low AS tooLow, avg_points AS avgPoints, persona_name AS personaName, " +
            "avatar_full AS avatarFull, blurdata_avatar_full AS blurdataAvatarFull, profile_url AS profileUrl " +
            "FROM mv_leaderboard_weekly ORDER BY total_points DESC", nativeQuery = true)
    List<LeaderboardMvRow> findWeekly();

    @Query(value = "SELECT steam_id AS steamId, total_points AS totalPoints, rounds, hits, flops, " +
            "too_high AS tooHigh, too_low AS tooLow, avg_points AS avgPoints, persona_name AS personaName, " +
            "avatar_full AS avatarFull, blurdata_avatar_full AS blurdataAvatarFull, profile_url AS profileUrl " +
            "FROM mv_leaderboard_season ORDER BY total_points DESC", nativeQuery = true)
    List<LeaderboardMvRow> findSeason();
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
git commit -m "feat(repository): add LeaderboardMvRepository read queries for leaderboard MVs"
```

---

### Task 3: `LeaderboardService` MV-backed builders

**Files:**
- Modify: `backend/src/main/java/org/steam5/service/LeaderboardService.java`
- Modify: `backend/src/main/java/org/steam5/repository/GuessRepository.java` (remove now-dead `aggregateAllTimeStats()`, fix a dangling Javadoc `@link`)
- Modify: `backend/src/test/java/org/steam5/service/LeaderboardServiceTest.java`

**Interfaces:**
- Consumes: `LeaderboardMvRepository` from Task 2.
- Produces: `LeaderboardService.buildMonthlyLeaderboard(LocalDate)`, `.buildWeeklyLeaderboard(LocalDate)`, `.buildSeasonLeaderboard(LocalDate)`, and the reimplemented `.buildAllTimeLeaderboard(LocalDate)` — all consumed by Task 4's controller. Constructor becomes 3-arg: `LeaderboardService(GuessRepository, UserRepository, LeaderboardMvRepository)`.

- [ ] **Step 1: Update `LeaderboardServiceTest.java` to mock the new MV-backed methods**

Replace the entire file with:

```java
package org.steam5.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.steam5.domain.Guess;
import org.steam5.domain.User;
import org.steam5.repository.GuessRepository;
import org.steam5.repository.LeaderboardMvRepository;
import org.steam5.repository.UserRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LeaderboardServiceTest {

    private GuessRepository guessRepository;
    private UserRepository userRepository;
    private LeaderboardMvRepository leaderboardMvRepository;
    private LeaderboardService service;

    @BeforeEach
    void setUp() {
        guessRepository = mock(GuessRepository.class);
        userRepository = mock(UserRepository.class);
        leaderboardMvRepository = mock(LeaderboardMvRepository.class);
        service = new LeaderboardService(guessRepository, userRepository, leaderboardMvRepository);
    }

    @Test
    void buildLeaderboard_returnsAggregatedLeaders() {
        LocalDate asOfDate = LocalDate.now();
        Guess g1 = new Guess(1L, "u1", asOfDate, 1, 100L, "1-100", "1-100", 5, OffsetDateTime.now());
        Guess g2 = new Guess(2L, "u1", asOfDate, 2, 200L, "101-1000", "1001-10000", 3, OffsetDateTime.now());
        Guess g3 = new Guess(3L, "u2", asOfDate, 1, 300L, "1001-10000", "101-1000", 1, OffsetDateTime.now());
        List<Guess> guesses = List.of(g1, g2, g3);

        User u1 = new User();
        u1.setSteamId("u1");
        u1.setPersonaName("User One");
        when(userRepository.findAllById(any())).thenReturn(List.of(u1));

        List<LeaderboardService.LeaderEntry> result = service.buildLeaderboard(guesses, asOfDate);

        assertEquals(2, result.size());

        LeaderboardService.LeaderEntry first = result.get(0);
        assertEquals("u1", first.steamId());
        assertEquals("User One", first.personaName());
        assertEquals(8L, first.totalPoints());
        assertEquals(2L, first.rounds());
        assertEquals(1L, first.hits());
        assertEquals(0L, first.flops());
        assertEquals(0L, first.tooHigh());
        assertEquals(1L, first.tooLow());
        assertEquals(4.0, first.avgPoints());
        assertEquals(0, first.streak());

        LeaderboardService.LeaderEntry second = result.get(1);
        assertEquals("u2", second.steamId());
        assertEquals("u2", second.personaName()); // no User record found — falls back to steamId
        assertEquals(1L, second.totalPoints());
        assertEquals(1L, second.rounds());
        assertEquals(0L, second.hits());
        assertEquals(1L, second.tooHigh());
        assertEquals(0L, second.tooLow());
        assertEquals(1.0, second.avgPoints());
    }

    @Test
    void buildLeaderboard_emptyGuesses_returnsEmptyList() {
        assertEquals(List.of(), service.buildLeaderboard(List.of(), LocalDate.now()));
    }

    @Test
    void buildAllTimeLeaderboard_returnsAggregatedLeaders() {
        // all-time aggregates come from mv_leaderboard_all_time, pre-ordered by total points
        // descending (see mv-leaderboard-all-time.sql) — not walked from raw Guess rows.
        final LeaderboardMvRepository.LeaderboardMvRow r1 = mock(LeaderboardMvRepository.LeaderboardMvRow.class);
        when(r1.getSteamId()).thenReturn("u1");
        when(r1.getTotalPoints()).thenReturn(5L);
        when(r1.getRounds()).thenReturn(1L);
        when(r1.getHits()).thenReturn(1L);
        when(r1.getFlops()).thenReturn(0L);
        when(r1.getTooHigh()).thenReturn(0L);
        when(r1.getTooLow()).thenReturn(0L);
        when(r1.getAvgPoints()).thenReturn(5.0);
        when(r1.getPersonaName()).thenReturn("User One");

        final LeaderboardMvRepository.LeaderboardMvRow r2 = mock(LeaderboardMvRepository.LeaderboardMvRow.class);
        when(r2.getSteamId()).thenReturn("u2");
        when(r2.getTotalPoints()).thenReturn(1L);
        when(r2.getRounds()).thenReturn(1L);
        when(r2.getHits()).thenReturn(0L);
        when(r2.getFlops()).thenReturn(0L);
        when(r2.getTooHigh()).thenReturn(1L);
        when(r2.getTooLow()).thenReturn(0L);
        when(r2.getAvgPoints()).thenReturn(1.0);
        // r2.getPersonaName() intentionally left unstubbed (null) — exercises the steamId fallback

        when(leaderboardMvRepository.findAllTime()).thenReturn(List.of(r1, r2));
        // findDistinctDatesUpToForUsers intentionally left unstubbed —
        // Mockito's empty-list default exercises the null-safe streak fallback path.

        List<LeaderboardService.LeaderEntry> result = service.buildAllTimeLeaderboard(LocalDate.now());

        assertEquals(2, result.size());
        assertEquals("u1", result.get(0).steamId());
        assertEquals("User One", result.get(0).personaName());
        assertEquals(5L, result.get(0).totalPoints());
        assertEquals(0, result.get(0).streak());
        assertEquals("u2", result.get(1).steamId());
        assertEquals("u2", result.get(1).personaName());
    }

    @Test
    void buildAllTimeLeaderboard_noRows_returnsEmptyList() {
        when(leaderboardMvRepository.findAllTime()).thenReturn(List.of());
        assertEquals(List.of(), service.buildAllTimeLeaderboard(LocalDate.now()));
    }

    @Test
    void buildMonthlyLeaderboard_delegatesToMonthlyMv() {
        final LeaderboardMvRepository.LeaderboardMvRow row = mock(LeaderboardMvRepository.LeaderboardMvRow.class);
        when(row.getSteamId()).thenReturn("u1");
        when(row.getTotalPoints()).thenReturn(10L);
        when(row.getRounds()).thenReturn(2L);
        when(row.getHits()).thenReturn(1L);
        when(row.getFlops()).thenReturn(0L);
        when(row.getTooHigh()).thenReturn(0L);
        when(row.getTooLow()).thenReturn(1L);
        when(row.getAvgPoints()).thenReturn(5.0);

        when(leaderboardMvRepository.findMonthly()).thenReturn(List.of(row));

        List<LeaderboardService.LeaderEntry> result = service.buildMonthlyLeaderboard(LocalDate.now());

        assertEquals(1, result.size());
        assertEquals("u1", result.get(0).steamId());
        assertEquals(10L, result.get(0).totalPoints());
    }

    @Test
    void buildWeeklyLeaderboard_delegatesToWeeklyMv() {
        final LeaderboardMvRepository.LeaderboardMvRow row = mock(LeaderboardMvRepository.LeaderboardMvRow.class);
        when(row.getSteamId()).thenReturn("u1");
        when(row.getTotalPoints()).thenReturn(7L);
        when(row.getRounds()).thenReturn(2L);
        when(row.getHits()).thenReturn(1L);
        when(row.getFlops()).thenReturn(0L);
        when(row.getTooHigh()).thenReturn(1L);
        when(row.getTooLow()).thenReturn(0L);
        when(row.getAvgPoints()).thenReturn(3.5);

        when(leaderboardMvRepository.findWeekly()).thenReturn(List.of(row));

        List<LeaderboardService.LeaderEntry> result = service.buildWeeklyLeaderboard(LocalDate.now());

        assertEquals(1, result.size());
        assertEquals("u1", result.get(0).steamId());
        assertEquals(7L, result.get(0).totalPoints());
    }

    @Test
    void buildSeasonLeaderboard_delegatesToSeasonMv() {
        final LeaderboardMvRepository.LeaderboardMvRow row = mock(LeaderboardMvRepository.LeaderboardMvRow.class);
        when(row.getSteamId()).thenReturn("u1");
        when(row.getTotalPoints()).thenReturn(20L);
        when(row.getRounds()).thenReturn(4L);
        when(row.getHits()).thenReturn(2L);
        when(row.getFlops()).thenReturn(1L);
        when(row.getTooHigh()).thenReturn(1L);
        when(row.getTooLow()).thenReturn(0L);
        when(row.getAvgPoints()).thenReturn(5.0);

        when(leaderboardMvRepository.findSeason()).thenReturn(List.of(row));

        List<LeaderboardService.LeaderEntry> result = service.buildSeasonLeaderboard(LocalDate.now());

        assertEquals(1, result.size());
        assertEquals("u1", result.get(0).steamId());
        assertEquals(20L, result.get(0).totalPoints());
    }
}
```

- [ ] **Step 2: Run the test to confirm it fails to compile**

```bash
/Users/nerlich/workspace/luca/steam5/backend/gradlew -p /Users/nerlich/workspace/luca/steam5/backend test --tests "org.steam5.service.LeaderboardServiceTest"
```

Expected: compile error — `LeaderboardService(GuessRepository, UserRepository, LeaderboardMvRepository)` doesn't exist yet (2-arg constructor only), and `buildMonthlyLeaderboard`/`buildWeeklyLeaderboard`/`buildSeasonLeaderboard` are undefined.

- [ ] **Step 3: Add the `leaderboardMvRepository` field and MV-backed builder methods**

In `LeaderboardService.java`, add the import and field:

```java
import org.steam5.repository.LeaderboardMvRepository;
```

```java
    private final GuessRepository guessRepository;
    private final UserRepository userRepository;
    private final LeaderboardMvRepository leaderboardMvRepository;
```

Find:

```java
    /**
     * Builds an all-time leaderboard from precomputed statistics while calculating each user's current streak.
     *
     * @param today the date used to calculate current streaks
     * @return leaderboard entries in the order provided by the aggregated statistics
     */
    public List<LeaderEntry> buildAllTimeLeaderboard(final LocalDate today) {
        final List<GuessRepository.AllTimeStatsRow> rows = guessRepository.aggregateAllTimeStats();
        if (rows.isEmpty()) {
            return List.of();
        }

        final List<String> steamIds = rows.stream().map(GuessRepository.AllTimeStatsRow::getSteamId).toList();
        final Map<String, User> usersById = userRepository.findAllById(steamIds).stream()
                .collect(Collectors.toMap(User::getSteamId, user -> user));
        final Map<String, List<LocalDate>> streakDatesById = guessRepository
                .findDistinctDatesUpToForUsers(steamIds, today)
                .stream()
                .collect(Collectors.groupingBy(GuessRepository.UserDateRow::getSteamId,
                        Collectors.mapping(GuessRepository.UserDateRow::getGameDate, Collectors.toList())));

        return rows.stream()
                .map(row -> {
                    final User user = usersById.get(row.getSteamId());
                    final List<LocalDate> dates = streakDatesById.getOrDefault(row.getSteamId(), List.of());
                    final int streak = StreakCalculator.currentStreak(dates, today);
                    return getLeaderEntry(
                            row.getSteamId(),
                            row.getTotalPoints() != null ? row.getTotalPoints() : 0L,
                            row.getRounds() != null ? row.getRounds() : 0L,
                            row.getHits() != null ? row.getHits() : 0L,
                            row.getFlops() != null ? row.getFlops() : 0L,
                            row.getTooHigh() != null ? row.getTooHigh() : 0L,
                            row.getTooLow() != null ? row.getTooLow() : 0L,
                            row.getAvgPoints() != null ? row.getAvgPoints() : 0.0,
                            streak,
                            user
                    );
                })
                .toList();
    }
```

Replace it with:

```java
    /**
     * Builds the all-time leaderboard from the {@code mv_leaderboard_all_time} materialized
     * view (see backend/src/main/resources/db/mv-leaderboard-all-time.sql), overlaying each
     * player's current streak from the live guesses table.
     *
     * @param today the date used to calculate current streaks
     * @return leaderboard entries in the order provided by the materialized view (total points descending)
     */
    public List<LeaderEntry> buildAllTimeLeaderboard(final LocalDate today) {
        return buildFromMv(leaderboardMvRepository.findAllTime(), today);
    }

    /**
     * Builds the rolling 30-day leaderboard from {@code mv_leaderboard_monthly}.
     * See backend/src/main/resources/db/mv-leaderboard-monthly.sql for the window definition.
     *
     * @param today the date used to calculate current streaks
     */
    public List<LeaderEntry> buildMonthlyLeaderboard(final LocalDate today) {
        return buildFromMv(leaderboardMvRepository.findMonthly(), today);
    }

    /**
     * Builds the rolling 7-day ("floating") leaderboard from {@code mv_leaderboard_weekly}.
     * The non-floating (previous full Monday-Sunday week) variant is not backed by this view
     * and continues to be computed live in {@code LeaderboardController#weekly}.
     * See backend/src/main/resources/db/mv-leaderboard-weekly.sql for the window definition.
     *
     * @param today the date used to calculate current streaks
     */
    public List<LeaderEntry> buildWeeklyLeaderboard(final LocalDate today) {
        return buildFromMv(leaderboardMvRepository.findWeekly(), today);
    }

    /**
     * Builds the current-season leaderboard from {@code mv_leaderboard_season}, which scopes
     * itself to whichever season row currently contains the database's CURRENT_DATE.
     * See backend/src/main/resources/db/mv-leaderboard-season.sql.
     *
     * @param asOfDate the date used to calculate current streaks (the earlier of "today" and
     *                 the season's end date, matching the season endpoint's existing behavior)
     */
    public List<LeaderEntry> buildSeasonLeaderboard(final LocalDate asOfDate) {
        return buildFromMv(leaderboardMvRepository.findSeason(), asOfDate);
    }

    /**
     * Shared assembly step for the four materialized-view-backed leaderboards: overlays each
     * row's current streak (computed live from {@code findDistinctDatesUpToForUsers}) onto the
     * pre-aggregated MV columns. Row order (total points descending) comes from the MV query.
     */
    private List<LeaderEntry> buildFromMv(final List<LeaderboardMvRepository.LeaderboardMvRow> rows, final LocalDate asOfDate) {
        if (rows.isEmpty()) {
            return List.of();
        }

        final List<String> steamIds = rows.stream().map(LeaderboardMvRepository.LeaderboardMvRow::getSteamId).toList();
        final Map<String, List<LocalDate>> streakDatesById = guessRepository
                .findDistinctDatesUpToForUsers(steamIds, asOfDate)
                .stream()
                .collect(Collectors.groupingBy(GuessRepository.UserDateRow::getSteamId,
                        Collectors.mapping(GuessRepository.UserDateRow::getGameDate, Collectors.toList())));

        return rows.stream()
                .map(row -> {
                    final List<LocalDate> dates = streakDatesById.getOrDefault(row.getSteamId(), List.of());
                    final int streak = StreakCalculator.currentStreak(dates, asOfDate);
                    final String personaName = row.getPersonaName() != null && !row.getPersonaName().isBlank()
                            ? row.getPersonaName() : row.getSteamId();
                    return new LeaderEntry(
                            row.getSteamId(),
                            personaName,
                            row.getTotalPoints() != null ? row.getTotalPoints() : 0L,
                            row.getRounds() != null ? row.getRounds() : 0L,
                            row.getHits() != null ? row.getHits() : 0L,
                            row.getFlops() != null ? row.getFlops() : 0L,
                            row.getTooHigh() != null ? row.getTooHigh() : 0L,
                            row.getTooLow() != null ? row.getTooLow() : 0L,
                            row.getAvgPoints() != null ? row.getAvgPoints() : 0.0,
                            streak,
                            blankToNull(row.getAvatarFull()),
                            blankToNull(row.getBlurdataAvatarFull()),
                            blankToNull(row.getProfileUrl())
                    );
                })
                .toList();
    }

    private static String blankToNull(final String value) {
        return value != null && !value.isBlank() ? value : null;
    }
```

Do **not** touch `buildEntry` or `getLeaderEntry` below this — they remain exactly as-is; only the newly-added code above reuses `blankToNull`.

- [ ] **Step 4: Delete the now-dead `aggregateAllTimeStats()` from `GuessRepository.java`**

Find (and delete) this method and its Javadoc in `GuessRepository.java`:

```java
    /**
     * All-time leaderboard aggregated entirely in SQL.
     * Avoids loading raw Guess entities into memory for the all-time view.
     * tooHigh/tooLow are computed by extracting the leading numeric threshold
     * from each bucket label string (mirrors bucketOrderFromLabel in the controller).
     */
    interface AllTimeStatsRow {
```

Wait — do **not** delete the `AllTimeStatsRow` interface (it's still used by `aggregateAllTimeStatsHavingMinRounds`). Only delete this method, further down:

```java
    @Query(value = """
            SELECT
                g.steam_id                                                          AS steamId,
                SUM(g.points)                                                       AS totalPoints,
                COUNT(*)                                                            AS rounds,
                SUM(CASE WHEN g.selected_bucket = g.actual_bucket THEN 1 ELSE 0 END) AS hits,
                SUM(CASE WHEN g.points = 0 THEN 1 ELSE 0 END)                      AS flops,
                SUM(CASE WHEN
                    CAST(NULLIF(regexp_replace(g.selected_bucket, '^(\\d+).*', '\\1'), '') AS BIGINT) >
                    CAST(NULLIF(regexp_replace(g.actual_bucket,   '^(\\d+).*', '\\1'), '') AS BIGINT)
                THEN 1 ELSE 0 END)                                                  AS tooHigh,
                SUM(CASE WHEN
                    CAST(NULLIF(regexp_replace(g.selected_bucket, '^(\\d+).*', '\\1'), '') AS BIGINT) <
                    CAST(NULLIF(regexp_replace(g.actual_bucket,   '^(\\d+).*', '\\1'), '') AS BIGINT)
                THEN 1 ELSE 0 END)                                                  AS tooLow,
                AVG(g.points)                                                       AS avgPoints
            FROM guesses g
            GROUP BY g.steam_id
            ORDER BY SUM(g.points) DESC
            """, nativeQuery = true)
    List<AllTimeStatsRow> aggregateAllTimeStats();

    /**
     * Same metrics as {@link #aggregateAllTimeStats()} but filters results to players
     * with at least {@code minRounds} guesses using a HAVING clause. Note that the HAVING
     * condition limits which aggregated groups are returned to Java, but does not avoid
     * scanning or aggregating the full table at the database level.
     */
```

Delete the `aggregateAllTimeStats()` method entirely, and update the Javadoc immediately above `aggregateAllTimeStatsHavingMinRounds` (the text shown above) to:

```java
    /**
     * Same metrics as the all-time leaderboard materialized view (see
     * backend/src/main/resources/db/mv-leaderboard-all-time.sql) but filters results to
     * players with at least {@code minRounds} guesses using a HAVING clause. Note that the
     * HAVING condition limits which aggregated groups are returned to Java, but does not
     * avoid scanning or aggregating the full table at the database level.
     */
```

Also update the earlier Javadoc reference on the `AllTimeStatsRow` interface itself — find:

```java
    /**
     * All-time leaderboard aggregated entirely in SQL.
     * Avoids loading raw Guess entities into memory for the all-time view.
     * tooHigh/tooLow are computed by extracting the leading numeric threshold
     * from each bucket label string (mirrors bucketOrderFromLabel in the controller).
     */
    interface AllTimeStatsRow {
```

Replace with:

```java
    /**
     * Row shape shared with the leaderboard materialized views (see
     * backend/src/main/resources/db/mv-leaderboard-*.sql) and {@link #aggregateAllTimeStatsHavingMinRounds}.
     * tooHigh/tooLow are computed by extracting the leading numeric threshold from each bucket
     * label string (mirrors bucketOrderFromLabel in the controller).
     */
    interface AllTimeStatsRow {
```

- [ ] **Step 5: Run the test to confirm it passes**

```bash
/Users/nerlich/workspace/luca/steam5/backend/gradlew -p /Users/nerlich/workspace/luca/steam5/backend test --tests "org.steam5.service.LeaderboardServiceTest"
```

Expected: `BUILD SUCCESSFUL`, all tests green.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/org/steam5/service/LeaderboardService.java backend/src/main/java/org/steam5/repository/GuessRepository.java backend/src/test/java/org/steam5/service/LeaderboardServiceTest.java
git commit -m "feat(service): build monthly/weekly/season/all-time leaderboards from materialized views"
```

---

### Task 4: `LeaderboardController` wiring

**Files:**
- Modify: `backend/src/main/java/org/steam5/web/LeaderboardController.java`
- Modify: `backend/src/test/java/org/steam5/web/LeaderboardControllerTest.java`

**Interfaces:**
- Consumes: `LeaderboardService.buildMonthlyLeaderboard/buildWeeklyLeaderboard/buildSeasonLeaderboard/buildAllTimeLeaderboard` from Task 3.
- Produces: no new public interface — endpoint behavior/contract is unchanged; only the internal data source changes for the four static leaderboard types.

- [ ] **Step 1: Update `LeaderboardControllerTest.java` with delegation tests for weekly/monthly/season**

Replace the entire file with:

```java
package org.steam5.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.ResponseEntity;
import org.steam5.domain.Guess;
import org.steam5.domain.ReviewGamePick;
import org.steam5.domain.Season;
import org.steam5.repository.GuessRepository;
import org.steam5.service.LeaderboardService;
import org.steam5.service.ReviewGameStateService;
import org.steam5.service.SeasonService;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class LeaderboardControllerTest {

    private GuessRepository guessRepository;
    private ReviewGameStateService reviewGameStateService;
    private SeasonService seasonService;
    private CacheManager cacheManager;
    private LeaderboardService leaderboardService;

    @BeforeEach
    void setUp() {
        guessRepository = mock(GuessRepository.class);
        reviewGameStateService = mock(ReviewGameStateService.class);
        seasonService = mock(SeasonService.class);
        cacheManager = mock(CacheManager.class);
        leaderboardService = mock(LeaderboardService.class);
    }

    @Test
    void today_fetchesGuessesThenDelegatesToService() {
        LeaderboardController c = new LeaderboardController(guessRepository, reviewGameStateService, seasonService, cacheManager, leaderboardService);
        LocalDate pickDate = LocalDate.now();
        when(reviewGameStateService.generateDailyPicks()).thenReturn(List.of(new ReviewGamePick(1L, pickDate, 42L, OffsetDateTime.now())));

        Guess g1 = new Guess(1L, "u1", pickDate, 1, 100L, "1-100", "1-100", 5, OffsetDateTime.now());
        List<Guess> guesses = List.of(g1);
        when(guessRepository.findAllByDate(pickDate)).thenReturn(guesses);

        List<LeaderboardService.LeaderEntry> canned = List.of(
                new LeaderboardService.LeaderEntry("u1", "User One", 5L, 1L, 1L, 0L, 0L, 0L, 5.0, 1, null, null, null)
        );
        when(leaderboardService.buildLeaderboard(guesses, pickDate)).thenReturn(canned);

        ResponseEntity<List<LeaderboardService.LeaderEntry>> res = c.today();
        assertEquals(200, res.getStatusCode().value());
        assertNotNull(res.getBody());
        assertSame(canned, res.getBody());
        verify(guessRepository).findAllByDate(pickDate);
        verify(leaderboardService).buildLeaderboard(guesses, pickDate);
    }

    @Test
    void allTime_delegatesToService() {
        LeaderboardController c = new LeaderboardController(guessRepository, reviewGameStateService, seasonService, cacheManager, leaderboardService);

        List<LeaderboardService.LeaderEntry> canned = List.of(
                new LeaderboardService.LeaderEntry("u1", "User One", 5L, 1L, 1L, 0L, 0L, 0L, 5.0, 1, null, null, null),
                new LeaderboardService.LeaderEntry("u2", "u2", 1L, 1L, 0L, 0L, 1L, 0L, 1.0, 0, null, null, null)
        );
        when(leaderboardService.buildAllTimeLeaderboard(any(LocalDate.class))).thenReturn(canned);

        ResponseEntity<List<LeaderboardService.LeaderEntry>> res = c.allTime();
        assertEquals(200, res.getStatusCode().value());
        assertSame(canned, res.getBody());
        verify(leaderboardService).buildAllTimeLeaderboard(any(LocalDate.class));
        verifyNoInteractions(guessRepository);
    }

    @Test
    void weekly_floating_delegatesToMvBackedService() {
        LeaderboardController c = new LeaderboardController(guessRepository, reviewGameStateService, seasonService, cacheManager, leaderboardService);
        when(reviewGameStateService.generateDailyPicks()).thenReturn(List.of());

        List<LeaderboardService.LeaderEntry> canned = List.of(
                new LeaderboardService.LeaderEntry("u1", "User One", 7L, 2L, 1L, 0L, 0L, 1L, 3.5, 1, null, null, null)
        );
        when(leaderboardService.buildWeeklyLeaderboard(any(LocalDate.class))).thenReturn(canned);

        ResponseEntity<List<LeaderboardService.LeaderEntry>> res = c.weekly(true);

        assertEquals(200, res.getStatusCode().value());
        assertSame(canned, res.getBody());
        verify(leaderboardService).buildWeeklyLeaderboard(any(LocalDate.class));
        verifyNoInteractions(guessRepository);
    }

    @Test
    void weekly_nonFloating_usesLiveQueryNotMv() {
        LeaderboardController c = new LeaderboardController(guessRepository, reviewGameStateService, seasonService, cacheManager, leaderboardService);
        when(reviewGameStateService.generateDailyPicks()).thenReturn(List.of());
        when(guessRepository.findAllBetween(any(LocalDate.class), any(LocalDate.class))).thenReturn(List.of());
        when(leaderboardService.buildLeaderboard(any(), any())).thenReturn(List.of());

        ResponseEntity<List<LeaderboardService.LeaderEntry>> res = c.weekly(false);

        assertEquals(200, res.getStatusCode().value());
        verify(guessRepository).findAllBetween(any(LocalDate.class), any(LocalDate.class));
        verify(leaderboardService).buildLeaderboard(any(), any());
        verify(leaderboardService, never()).buildWeeklyLeaderboard(any());
    }

    @Test
    void monthly_delegatesToMvBackedService() {
        LeaderboardController c = new LeaderboardController(guessRepository, reviewGameStateService, seasonService, cacheManager, leaderboardService);
        when(reviewGameStateService.generateDailyPicks()).thenReturn(List.of());

        List<LeaderboardService.LeaderEntry> canned = List.of(
                new LeaderboardService.LeaderEntry("u1", "User One", 10L, 2L, 1L, 0L, 0L, 1L, 5.0, 1, null, null, null)
        );
        when(leaderboardService.buildMonthlyLeaderboard(any(LocalDate.class))).thenReturn(canned);

        ResponseEntity<List<LeaderboardService.LeaderEntry>> res = c.monthly();

        assertEquals(200, res.getStatusCode().value());
        assertSame(canned, res.getBody());
        verify(leaderboardService).buildMonthlyLeaderboard(any(LocalDate.class));
        verifyNoInteractions(guessRepository);
    }

    @Test
    void season_cacheMiss_delegatesToMvBackedServiceAndCachesResult() {
        LeaderboardController c = new LeaderboardController(guessRepository, reviewGameStateService, seasonService, cacheManager, leaderboardService);
        LocalDate today = LocalDate.now();

        Season season = new Season();
        season.setSeasonNumber(3);
        season.setStartDate(today.minusDays(10));
        season.setEndDate(today.plusDays(20));
        when(seasonService.findSeasonContaining(any(LocalDate.class))).thenReturn(Optional.of(season));

        Cache cache = mock(Cache.class);
        when(cacheManager.getCache("leaderboard-static")).thenReturn(cache);
        when(cache.get(anyString())).thenReturn(null);

        List<LeaderboardService.LeaderEntry> canned = List.of(
                new LeaderboardService.LeaderEntry("u1", "User One", 20L, 4L, 2L, 1L, 1L, 0L, 5.0, 1, null, null, null)
        );
        when(leaderboardService.buildSeasonLeaderboard(any(LocalDate.class))).thenReturn(canned);

        ResponseEntity<List<LeaderboardService.LeaderEntry>> res = c.season();

        assertEquals(200, res.getStatusCode().value());
        assertSame(canned, res.getBody());
        verify(leaderboardService).buildSeasonLeaderboard(any(LocalDate.class));
        verify(cache).put(anyString(), any());
    }

    @Test
    void season_cacheHit_skipsMvBackedService() {
        LeaderboardController c = new LeaderboardController(guessRepository, reviewGameStateService, seasonService, cacheManager, leaderboardService);
        LocalDate today = LocalDate.now();

        Season season = new Season();
        season.setSeasonNumber(3);
        season.setStartDate(today.minusDays(10));
        season.setEndDate(today.plusDays(20));
        when(seasonService.findSeasonContaining(any(LocalDate.class))).thenReturn(Optional.of(season));

        List<LeaderboardService.LeaderEntry> cached = List.of(
                new LeaderboardService.LeaderEntry("u1", "User One", 20L, 4L, 2L, 1L, 1L, 0L, 5.0, 1, null, null, null)
        );
        Cache cache = mock(Cache.class);
        when(cacheManager.getCache("leaderboard-static")).thenReturn(cache);
        Cache.ValueWrapper wrapper = () -> cached;
        when(cache.get(anyString())).thenReturn(wrapper);

        ResponseEntity<List<LeaderboardService.LeaderEntry>> res = c.season();

        assertEquals(200, res.getStatusCode().value());
        assertSame(cached, res.getBody());
        verifyNoInteractions(leaderboardService);
    }
}
```

- [ ] **Step 2: Run the test to confirm the new tests fail**

```bash
/Users/nerlich/workspace/luca/steam5/backend/gradlew -p /Users/nerlich/workspace/luca/steam5/backend test --tests "org.steam5.web.LeaderboardControllerTest"
```

Expected: compile error — `LeaderboardService.buildWeeklyLeaderboard`/`buildMonthlyLeaderboard`/`buildSeasonLeaderboard` mock stubs are unresolved (these exist after Task 3, so this should actually compile — the failures here should instead be `weekly_floating_...` and `monthly_...` asserting `verifyNoInteractions(guessRepository)`/`buildMonthlyLeaderboard` calls that the current controller doesn't make yet). Confirm those two tests fail with assertion errors (unwanted `guessRepository` interactions / missing `buildWeeklyLeaderboard`/`buildMonthlyLeaderboard`/`buildSeasonLeaderboard` invocations), while `today_`/`allTime_` still pass.

- [ ] **Step 3: Update `LeaderboardController.java`**

Find:

```java
    /**
     * Builds the weekly leaderboard for either the current rolling period or the previous full week.
     *
     * @param floating whether to include the seven days ending on the current date; otherwise, uses the
     *                 Monday-through-Sunday week immediately before the current week
     * @return leaderboard entries for the selected period
     */
    @GetMapping("/weekly")
    @Cacheable(value = "leaderboard-static", key = "'weekly:' + #floating + ':' + T(org.steam5.domain.GameDate).todayUtc()", unless = "#result == null || #result.body == null")
    public ResponseEntity<List<LeaderboardService.LeaderEntry>> weekly(@RequestParam(name = "floating", required = false, defaultValue = "false") boolean floating) {
        final List<ReviewGamePick> picks = reviewGameStateService.generateDailyPicks();

        final LocalDate today = picks.isEmpty() ? GameDate.todayUtc() : picks.getFirst().getPickDate();
        final LocalDate start;
        final LocalDate end;

        if (floating) {
            // last seven days including today
            end = today;
            start = today.minusDays(6);
        } else {
            // last full week: Monday..Sunday immediately before the current week
            final LocalDate startOfCurrentWeek = today.minusDays((today.getDayOfWeek().getValue() + 6) % 7L);
            start = startOfCurrentWeek.minusDays(7);
            end = startOfCurrentWeek.minusDays(1);
        }

        final List<Guess> guesses = guessRepository.findAllBetween(start, end);
        return ResponseEntity.ok(leaderboardService.buildLeaderboard(guesses, today));
    }

    /**
     * Builds the leaderboard for the 30-day period ending on the current game date.
     *
     * @return the leaderboard entries for the last 30 days, including the current game date
     */
    @GetMapping("/monthly")
    @Cacheable(value = "leaderboard-static", key = "'monthly:' + T(org.steam5.domain.GameDate).todayUtc()", unless = "#result == null || #result.body == null")
    public ResponseEntity<List<LeaderboardService.LeaderEntry>> monthly() {
        final List<ReviewGamePick> picks = reviewGameStateService.generateDailyPicks();
        final LocalDate today = picks.isEmpty() ? GameDate.todayUtc() : picks.getFirst().getPickDate();

        // Last 30 days including today
        final LocalDate start = today.minusDays(29);
        final LocalDate end = today;

        final List<Guess> guesses = guessRepository.findAllBetween(start, end);
        return ResponseEntity.ok(leaderboardService.buildLeaderboard(guesses, today));
    }

    /**
     * Builds the leaderboard for the current season through the current date or the season end date.
     *
     * @return the season leaderboard entries
     */
    @GetMapping("/season")
    public ResponseEntity<List<LeaderboardService.LeaderEntry>> season() {
        final LocalDate today = GameDate.todayUtc();
        final Season season = seasonService.findSeasonContaining(today)
                .orElseGet(() -> seasonService.ensureSeasonForDate(today));
        final LocalDate asOfDate = season.getEndDate().isBefore(today) ? season.getEndDate() : today;
        final String cacheKey = "season:" + season.getSeasonNumber() + ":" + asOfDate;
        final Cache cache = cacheManager.getCache("leaderboard-static");
        if (cache != null) {
            final Cache.ValueWrapper wrapper = cache.get(cacheKey);
            if (wrapper != null && wrapper.get() instanceof List<?> cached) {
                @SuppressWarnings("unchecked")
                final List<LeaderboardService.LeaderEntry> cachedEntries = (List<LeaderboardService.LeaderEntry>) cached;
                return ResponseEntity.ok(cachedEntries);
            }
        }

        final List<Guess> guesses = guessRepository.findAllBetween(season.getStartDate(), asOfDate);
        final ResponseEntity<List<LeaderboardService.LeaderEntry>> response = ResponseEntity.ok(leaderboardService.buildLeaderboard(guesses, asOfDate));
        if (cache != null && response.getBody() != null) {
            cache.put(cacheKey, response.getBody());
        }
        return response;
    }
```

Replace it with:

```java
    /**
     * Builds the weekly leaderboard for either the current rolling period or the previous full week.
     *
     * @param floating whether to include the seven days ending on the current date (served from
     *                 {@code mv_leaderboard_weekly}); otherwise, uses the Monday-through-Sunday week
     *                 immediately before the current week, computed live (not MV-backed — its window
     *                 doesn't match the MV's rolling definition)
     * @return leaderboard entries for the selected period
     */
    @GetMapping("/weekly")
    @Cacheable(value = "leaderboard-static", key = "'weekly:' + #floating + ':' + T(org.steam5.domain.GameDate).todayUtc()", unless = "#result == null || #result.body == null")
    public ResponseEntity<List<LeaderboardService.LeaderEntry>> weekly(@RequestParam(name = "floating", required = false, defaultValue = "false") boolean floating) {
        final List<ReviewGamePick> picks = reviewGameStateService.generateDailyPicks();
        final LocalDate today = picks.isEmpty() ? GameDate.todayUtc() : picks.getFirst().getPickDate();

        if (floating) {
            return ResponseEntity.ok(leaderboardService.buildWeeklyLeaderboard(today));
        }

        // last full week: Monday..Sunday immediately before the current week
        final LocalDate startOfCurrentWeek = today.minusDays((today.getDayOfWeek().getValue() + 6) % 7L);
        final LocalDate start = startOfCurrentWeek.minusDays(7);
        final LocalDate end = startOfCurrentWeek.minusDays(1);

        final List<Guess> guesses = guessRepository.findAllBetween(start, end);
        return ResponseEntity.ok(leaderboardService.buildLeaderboard(guesses, today));
    }

    /**
     * Builds the leaderboard for the 30-day period ending on the current game date, served from
     * {@code mv_leaderboard_monthly}.
     *
     * @return the leaderboard entries for the last 30 days, including the current game date
     */
    @GetMapping("/monthly")
    @Cacheable(value = "leaderboard-static", key = "'monthly:' + T(org.steam5.domain.GameDate).todayUtc()", unless = "#result == null || #result.body == null")
    public ResponseEntity<List<LeaderboardService.LeaderEntry>> monthly() {
        final List<ReviewGamePick> picks = reviewGameStateService.generateDailyPicks();
        final LocalDate today = picks.isEmpty() ? GameDate.todayUtc() : picks.getFirst().getPickDate();
        return ResponseEntity.ok(leaderboardService.buildMonthlyLeaderboard(today));
    }

    /**
     * Builds the leaderboard for the current season through the current date or the season end date,
     * served from {@code mv_leaderboard_season}.
     *
     * @return the season leaderboard entries
     */
    @GetMapping("/season")
    public ResponseEntity<List<LeaderboardService.LeaderEntry>> season() {
        final LocalDate today = GameDate.todayUtc();
        final Season season = seasonService.findSeasonContaining(today)
                .orElseGet(() -> seasonService.ensureSeasonForDate(today));
        final LocalDate asOfDate = season.getEndDate().isBefore(today) ? season.getEndDate() : today;
        final String cacheKey = "season:" + season.getSeasonNumber() + ":" + asOfDate;
        final Cache cache = cacheManager.getCache("leaderboard-static");
        if (cache != null) {
            final Cache.ValueWrapper wrapper = cache.get(cacheKey);
            if (wrapper != null && wrapper.get() instanceof List<?> cached) {
                @SuppressWarnings("unchecked")
                final List<LeaderboardService.LeaderEntry> cachedEntries = (List<LeaderboardService.LeaderEntry>) cached;
                return ResponseEntity.ok(cachedEntries);
            }
        }

        final List<LeaderboardService.LeaderEntry> entries = leaderboardService.buildSeasonLeaderboard(asOfDate);
        final ResponseEntity<List<LeaderboardService.LeaderEntry>> response = ResponseEntity.ok(entries);
        if (cache != null) {
            cache.put(cacheKey, entries);
        }
        return response;
    }
```

Note `allTime()` and `today()` are unchanged — leave them exactly as they are.

- [ ] **Step 4: Run the test to confirm it passes**

```bash
/Users/nerlich/workspace/luca/steam5/backend/gradlew -p /Users/nerlich/workspace/luca/steam5/backend test --tests "org.steam5.web.LeaderboardControllerTest"
```

Expected: `BUILD SUCCESSFUL`, all tests green.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/org/steam5/web/LeaderboardController.java backend/src/test/java/org/steam5/web/LeaderboardControllerTest.java
git commit -m "feat(web): route weekly/monthly/season/all-time leaderboard endpoints to MV-backed service methods"
```

---

### Task 5: `LeaderboardRefreshService` + repository refresh queries

**Files:**
- Modify: `backend/src/main/java/org/steam5/repository/LeaderboardMvRepository.java`
- Create: `backend/src/main/java/org/steam5/service/LeaderboardRefreshService.java`
- Create: `backend/src/test/java/org/steam5/service/LeaderboardRefreshServiceTest.java`

**Interfaces:**
- Produces: `LeaderboardRefreshService.refreshAllTime()/refreshMonthly()/refreshWeekly()/refreshSeason()`, consumed by Task 6's job.

- [ ] **Step 1: Write `LeaderboardRefreshServiceTest.java`**

```java
package org.steam5.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.steam5.repository.LeaderboardMvRepository;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LeaderboardRefreshServiceTest {

    private LeaderboardMvRepository leaderboardMvRepository;
    private LeaderboardRefreshService service;

    @BeforeEach
    void setUp() {
        leaderboardMvRepository = mock(LeaderboardMvRepository.class);
        service = new LeaderboardRefreshService(leaderboardMvRepository);
    }

    @Test
    void refreshAllTime_whenPopulated_usesConcurrentRefresh() {
        when(leaderboardMvRepository.isPopulated("mv_leaderboard_all_time")).thenReturn(true);

        service.refreshAllTime();

        verify(leaderboardMvRepository).refreshAllTimeConcurrently();
        verify(leaderboardMvRepository, never()).refreshAllTimeFull();
    }

    @Test
    void refreshAllTime_whenNotPopulated_fallsBackToFullRefresh() {
        when(leaderboardMvRepository.isPopulated("mv_leaderboard_all_time")).thenReturn(false);

        service.refreshAllTime();

        verify(leaderboardMvRepository).refreshAllTimeFull();
        verify(leaderboardMvRepository, never()).refreshAllTimeConcurrently();
    }

    @Test
    void refreshSeason_whenIsPopulatedReturnsNull_fallsBackToFullRefresh() {
        // A null result (e.g. the view row is missing from pg_matviews) must not NPE —
        // treat it the same as "not populated" so the failure surfaces from the REFRESH
        // statement itself (missing relation) rather than a silent skip.
        when(leaderboardMvRepository.isPopulated("mv_leaderboard_season")).thenReturn(null);

        service.refreshSeason();

        verify(leaderboardMvRepository).refreshSeasonFull();
    }

    @Test
    void refreshMonthly_whenPopulated_usesConcurrentRefresh() {
        when(leaderboardMvRepository.isPopulated("mv_leaderboard_monthly")).thenReturn(true);

        service.refreshMonthly();

        verify(leaderboardMvRepository).refreshMonthlyConcurrently();
        verify(leaderboardMvRepository, never()).refreshMonthlyFull();
    }

    @Test
    void refreshWeekly_whenPopulated_usesConcurrentRefresh() {
        when(leaderboardMvRepository.isPopulated("mv_leaderboard_weekly")).thenReturn(true);

        service.refreshWeekly();

        verify(leaderboardMvRepository).refreshWeeklyConcurrently();
        verify(leaderboardMvRepository, never()).refreshWeeklyFull();
    }
}
```

- [ ] **Step 2: Run it to confirm it fails to compile**

```bash
/Users/nerlich/workspace/luca/steam5/backend/gradlew -p /Users/nerlich/workspace/luca/steam5/backend test --tests "org.steam5.service.LeaderboardRefreshServiceTest"
```

Expected: compile error — `LeaderboardRefreshService` doesn't exist yet.

- [ ] **Step 3: Add refresh/isPopulated methods to `LeaderboardMvRepository.java`**

Add these imports:

```java
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
```

Add these methods inside the interface, after `findSeason()`:

```java
    @Query(value = "SELECT ispopulated FROM pg_matviews WHERE matviewname = :viewName", nativeQuery = true)
    Boolean isPopulated(@Param("viewName") String viewName);

    @Transactional
    @Modifying(clearAutomatically = false, flushAutomatically = false)
    @Query(value = "REFRESH MATERIALIZED VIEW mv_leaderboard_all_time", nativeQuery = true)
    void refreshAllTimeFull();

    @Transactional
    @Modifying(clearAutomatically = false, flushAutomatically = false)
    @Query(value = "REFRESH MATERIALIZED VIEW CONCURRENTLY mv_leaderboard_all_time", nativeQuery = true)
    void refreshAllTimeConcurrently();

    @Transactional
    @Modifying(clearAutomatically = false, flushAutomatically = false)
    @Query(value = "REFRESH MATERIALIZED VIEW mv_leaderboard_monthly", nativeQuery = true)
    void refreshMonthlyFull();

    @Transactional
    @Modifying(clearAutomatically = false, flushAutomatically = false)
    @Query(value = "REFRESH MATERIALIZED VIEW CONCURRENTLY mv_leaderboard_monthly", nativeQuery = true)
    void refreshMonthlyConcurrently();

    @Transactional
    @Modifying(clearAutomatically = false, flushAutomatically = false)
    @Query(value = "REFRESH MATERIALIZED VIEW mv_leaderboard_weekly", nativeQuery = true)
    void refreshWeeklyFull();

    @Transactional
    @Modifying(clearAutomatically = false, flushAutomatically = false)
    @Query(value = "REFRESH MATERIALIZED VIEW CONCURRENTLY mv_leaderboard_weekly", nativeQuery = true)
    void refreshWeeklyConcurrently();

    @Transactional
    @Modifying(clearAutomatically = false, flushAutomatically = false)
    @Query(value = "REFRESH MATERIALIZED VIEW mv_leaderboard_season", nativeQuery = true)
    void refreshSeasonFull();

    @Transactional
    @Modifying(clearAutomatically = false, flushAutomatically = false)
    @Query(value = "REFRESH MATERIALIZED VIEW CONCURRENTLY mv_leaderboard_season", nativeQuery = true)
    void refreshSeasonConcurrently();
```

(This mirrors the existing `@Transactional @Modifying(clearAutomatically = false, flushAutomatically = false)` pattern already used by `IngestStateRepository#upsert`.)

- [ ] **Step 4: Create `LeaderboardRefreshService.java`**

```java
package org.steam5.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.steam5.repository.LeaderboardMvRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class LeaderboardRefreshService {

    private final LeaderboardMvRepository leaderboardMvRepository;

    public void refreshAllTime() {
        refresh("mv_leaderboard_all_time", leaderboardMvRepository::refreshAllTimeConcurrently, leaderboardMvRepository::refreshAllTimeFull);
    }

    public void refreshMonthly() {
        refresh("mv_leaderboard_monthly", leaderboardMvRepository::refreshMonthlyConcurrently, leaderboardMvRepository::refreshMonthlyFull);
    }

    public void refreshWeekly() {
        refresh("mv_leaderboard_weekly", leaderboardMvRepository::refreshWeeklyConcurrently, leaderboardMvRepository::refreshWeeklyFull);
    }

    public void refreshSeason() {
        refresh("mv_leaderboard_season", leaderboardMvRepository::refreshSeasonConcurrently, leaderboardMvRepository::refreshSeasonFull);
    }

    /**
     * REFRESH MATERIALIZED VIEW CONCURRENTLY requires the view to already be populated
     * (see mv-leaderboard-*.sql, created WITH NO DATA). Before that first population, fall
     * back to a plain REFRESH so the job self-heals instead of failing forever.
     */
    private void refresh(final String viewName, final Runnable concurrentRefresh, final Runnable fullRefresh) {
        final boolean populated = Boolean.TRUE.equals(leaderboardMvRepository.isPopulated(viewName));
        if (populated) {
            concurrentRefresh.run();
        } else {
            log.info("{} not yet populated — running non-concurrent initial REFRESH", viewName);
            fullRefresh.run();
        }
    }
}
```

- [ ] **Step 5: Run the test to confirm it passes**

```bash
/Users/nerlich/workspace/luca/steam5/backend/gradlew -p /Users/nerlich/workspace/luca/steam5/backend test --tests "org.steam5.service.LeaderboardRefreshServiceTest"
```

Expected: `BUILD SUCCESSFUL`, all tests green.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/org/steam5/repository/LeaderboardMvRepository.java backend/src/main/java/org/steam5/service/LeaderboardRefreshService.java backend/src/test/java/org/steam5/service/LeaderboardRefreshServiceTest.java
git commit -m "feat(service): add LeaderboardRefreshService with populated-check fallback to non-concurrent refresh"
```

---

### Task 6: `DomainCacheEvictor.evictLeaderboardStatic()` + `LeaderboardRefreshJob`

**Files:**
- Modify: `backend/src/main/java/org/steam5/service/DomainCacheEvictor.java`
- Create: `backend/src/main/java/org/steam5/job/LeaderboardRefreshJob.java`
- Create: `backend/src/test/java/org/steam5/job/LeaderboardRefreshJobTest.java` (new package/directory — no `job` test package exists yet)

**Interfaces:**
- Consumes: `LeaderboardRefreshService` from Task 5.
- Produces: `LeaderboardRefreshJob` with `JobDetail` beans `LeaderboardRefreshJob_AllTime/_Monthly/_Weekly/_Season`, consumed by Task 7's `QuartzConfig` triggers.

- [ ] **Step 1: Write `LeaderboardRefreshJobTest.java`**

```java
package org.steam5.job;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.steam5.service.DomainCacheEvictor;
import org.steam5.service.LeaderboardRefreshService;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LeaderboardRefreshJobTest {

    private LeaderboardRefreshService refreshService;
    private DomainCacheEvictor cacheEvictor;
    private LeaderboardRefreshJob job;

    @BeforeEach
    void setUp() {
        refreshService = mock(LeaderboardRefreshService.class);
        cacheEvictor = mock(DomainCacheEvictor.class);
        job = new LeaderboardRefreshJob(refreshService, cacheEvictor);
    }

    private JobExecutionContext contextFor(String type) {
        JobExecutionContext context = mock(JobExecutionContext.class);
        JobDataMap map = new JobDataMap();
        map.put("type", type);
        when(context.getMergedJobDataMap()).thenReturn(map);
        return context;
    }

    @Test
    void execute_allTime_refreshesThenEvictsInOrder() throws JobExecutionException {
        job.execute(contextFor("ALL_TIME"));

        InOrder order = inOrder(refreshService, cacheEvictor);
        order.verify(refreshService).refreshAllTime();
        order.verify(cacheEvictor).evictLeaderboardStatic();
    }

    @Test
    void execute_monthly_refreshesThenEvicts() throws JobExecutionException {
        job.execute(contextFor("MONTHLY"));
        verify(refreshService).refreshMonthly();
        verify(cacheEvictor).evictLeaderboardStatic();
    }

    @Test
    void execute_weekly_refreshesThenEvicts() throws JobExecutionException {
        job.execute(contextFor("WEEKLY"));
        verify(refreshService).refreshWeekly();
        verify(cacheEvictor).evictLeaderboardStatic();
    }

    @Test
    void execute_season_refreshesThenEvicts() throws JobExecutionException {
        job.execute(contextFor("SEASON"));
        verify(refreshService).refreshSeason();
        verify(cacheEvictor).evictLeaderboardStatic();
    }

    @Test
    void execute_refreshFails_stillEvictsThenWrapsException() {
        doThrow(new RuntimeException("boom")).when(refreshService).refreshSeason();
        JobExecutionContext context = contextFor("SEASON");

        assertThrows(JobExecutionException.class, () -> job.execute(context));

        verify(cacheEvictor).evictLeaderboardStatic();
    }
}
```

- [ ] **Step 2: Run it to confirm it fails to compile**

```bash
/Users/nerlich/workspace/luca/steam5/backend/gradlew -p /Users/nerlich/workspace/luca/steam5/backend test --tests "org.steam5.job.LeaderboardRefreshJobTest"
```

Expected: compile error — `LeaderboardRefreshJob` and `DomainCacheEvictor#evictLeaderboardStatic` don't exist yet.

- [ ] **Step 3: Add `evictLeaderboardStatic()` to `DomainCacheEvictor.java`**

Find:

```java
    static final String REVIEW_GAME = "review-game";
    static final String ONE_DAY = "one-day";
```

Replace with:

```java
    static final String REVIEW_GAME = "review-game";
    static final String ONE_DAY = "one-day";
    static final String LEADERBOARD_STATIC = "leaderboard-static";
```

Then, immediately after the existing `evictAppDetail` method (before the `private void clear` helper), add:

```java
    /**
     * Drop cached leaderboard responses (all-time, monthly, weekly-floating, season). Call
     * after a leaderboard materialized view refresh, since the cached entries would otherwise
     * keep serving pre-refresh data for up to the cache's 10-minute TTL.
     */
    public void evictLeaderboardStatic() {
        clear(LEADERBOARD_STATIC);
    }
```

- [ ] **Step 4: Create `LeaderboardRefreshJob.java`**

```java
package org.steam5.job;

import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.steam5.service.DomainCacheEvictor;
import org.steam5.service.LeaderboardRefreshService;

import java.util.concurrent.TimeUnit;

/**
 * Refreshes one leaderboard materialized view per firing, driven by the "type" JobDataMap
 * entry set on each of the four JobDetail beans below. A single parameterized job class
 * (rather than four near-identical ones) keeps the refresh-then-evict flow in one place.
 */
@Component
@Slf4j
@DisallowConcurrentExecution
public class LeaderboardRefreshJob implements Job {

    public enum LeaderboardType { ALL_TIME, MONTHLY, WEEKLY, SEASON }

    private final LeaderboardRefreshService refreshService;
    private final DomainCacheEvictor cacheEvictor;

    public LeaderboardRefreshJob(LeaderboardRefreshService refreshService, DomainCacheEvictor cacheEvictor) {
        this.refreshService = refreshService;
        this.cacheEvictor = cacheEvictor;
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        final long start = System.nanoTime();
        final JobDataMap map = context.getMergedJobDataMap();
        final LeaderboardType type = LeaderboardType.valueOf(String.valueOf(map.get("type")));
        Exception caughtException = null;
        log.info("LeaderboardRefreshJob[{}] starting", type);
        try {
            switch (type) {
                case ALL_TIME -> refreshService.refreshAllTime();
                case MONTHLY -> refreshService.refreshMonthly();
                case WEEKLY -> refreshService.refreshWeekly();
                case SEASON -> refreshService.refreshSeason();
            }
        } catch (Exception e) {
            log.error("LeaderboardRefreshJob[{}] failed", type, e);
            caughtException = e;
        } finally {
            cacheEvictor.evictLeaderboardStatic();
            long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            log.info("LeaderboardRefreshJob[{}] completed in {}ms", type, ms);
            if (caughtException != null) {
                throw new JobExecutionException(caughtException, false);
            }
        }
    }

    @Bean("LeaderboardRefreshJob_AllTime")
    public JobDetail allTimeJobDetail() {
        return JobBuilder.newJob().ofType(LeaderboardRefreshJob.class)
                .storeDurably()
                .withIdentity("LeaderboardRefreshJob_AllTime")
                .usingJobData("type", LeaderboardType.ALL_TIME.name())
                .build();
    }

    @Bean("LeaderboardRefreshJob_Monthly")
    public JobDetail monthlyJobDetail() {
        return JobBuilder.newJob().ofType(LeaderboardRefreshJob.class)
                .storeDurably()
                .withIdentity("LeaderboardRefreshJob_Monthly")
                .usingJobData("type", LeaderboardType.MONTHLY.name())
                .build();
    }

    @Bean("LeaderboardRefreshJob_Weekly")
    public JobDetail weeklyJobDetail() {
        return JobBuilder.newJob().ofType(LeaderboardRefreshJob.class)
                .storeDurably()
                .withIdentity("LeaderboardRefreshJob_Weekly")
                .usingJobData("type", LeaderboardType.WEEKLY.name())
                .build();
    }

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

- [ ] **Step 5: Run the test to confirm it passes**

```bash
/Users/nerlich/workspace/luca/steam5/backend/gradlew -p /Users/nerlich/workspace/luca/steam5/backend test --tests "org.steam5.job.LeaderboardRefreshJobTest"
```

Expected: `BUILD SUCCESSFUL`, all tests green.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/org/steam5/service/DomainCacheEvictor.java backend/src/main/java/org/steam5/job/LeaderboardRefreshJob.java backend/src/test/java/org/steam5/job/LeaderboardRefreshJobTest.java
git commit -m "feat(job): add LeaderboardRefreshJob (refresh MV then evict leaderboard-static cache)"
```

---

### Task 7: Quartz triggers + `jobs.leaderboard-refresh-*` configuration

**Files:**
- Modify: `backend/src/main/java/org/steam5/config/QuartzConfig.java`
- Modify: `backend/src/main/resources/application.yml`
- Modify: `backend/src/main/resources/application-dev.yml`

**Interfaces:**
- Consumes: `LeaderboardRefreshJob`'s four `JobDetail` beans from Task 6.
- Produces: nothing further downstream — this is the last wiring task; Task 8 is documentation only.

No new unit test for this task (Quartz `Trigger`/`@ConditionalOnProperty` bean wiring has no existing test coverage anywhere in this codebase — `QuartzConfig`'s other trigger beans aren't tested either). Verification is a compile check plus a full-suite run to confirm nothing else broke.

- [ ] **Step 1: Add trigger beans to `QuartzConfig.java`**

Add these `@Bean` methods inside the `QuartzConfig` class, after `triggerPlayerSpotlightJob`, before the closing brace:

```java
    @Bean
    @ConditionalOnProperty(prefix = "jobs.leaderboard-refresh-all-time", name = "enabled", havingValue = "true", matchIfMissing = false)
    public Trigger triggerLeaderboardRefreshAllTimeNightly(@Qualifier("LeaderboardRefreshJob_AllTime") JobDetail job) {
        return TriggerBuilder.newTrigger().forJob(job)
                .withIdentity("LeaderboardRefreshJob_AllTime_Nightly_Trigger")
                // daily at 00:40 UTC — after seasons-finalizer (00:25) settles season boundaries
                .withSchedule(
                        CronScheduleBuilder.cronSchedule("0 40 0 * * ?")
                                .inTimeZone(TimeZone.getTimeZone("UTC"))
                )
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "jobs.leaderboard-refresh-all-time", name = "enabled", havingValue = "true", matchIfMissing = false)
    public Trigger triggerLeaderboardRefreshAllTimeIntraday(@Qualifier("LeaderboardRefreshJob_AllTime") JobDetail job) {
        return TriggerBuilder.newTrigger().forJob(job)
                .withIdentity("LeaderboardRefreshJob_AllTime_Intraday_Trigger")
                // every 10 minutes, matching the leaderboard-static Caffeine TTL
                .withSchedule(simpleSchedule().repeatForever().withIntervalInMinutes(10))
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "jobs.leaderboard-refresh-monthly", name = "enabled", havingValue = "true", matchIfMissing = false)
    public Trigger triggerLeaderboardRefreshMonthlyNightly(@Qualifier("LeaderboardRefreshJob_Monthly") JobDetail job) {
        return TriggerBuilder.newTrigger().forJob(job)
                .withIdentity("LeaderboardRefreshJob_Monthly_Nightly_Trigger")
                // daily at 00:42 UTC
                .withSchedule(
                        CronScheduleBuilder.cronSchedule("0 42 0 * * ?")
                                .inTimeZone(TimeZone.getTimeZone("UTC"))
                )
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "jobs.leaderboard-refresh-monthly", name = "enabled", havingValue = "true", matchIfMissing = false)
    public Trigger triggerLeaderboardRefreshMonthlyIntraday(@Qualifier("LeaderboardRefreshJob_Monthly") JobDetail job) {
        return TriggerBuilder.newTrigger().forJob(job)
                .withIdentity("LeaderboardRefreshJob_Monthly_Intraday_Trigger")
                .withSchedule(simpleSchedule().repeatForever().withIntervalInMinutes(10))
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "jobs.leaderboard-refresh-weekly", name = "enabled", havingValue = "true", matchIfMissing = false)
    public Trigger triggerLeaderboardRefreshWeeklyNightly(@Qualifier("LeaderboardRefreshJob_Weekly") JobDetail job) {
        return TriggerBuilder.newTrigger().forJob(job)
                .withIdentity("LeaderboardRefreshJob_Weekly_Nightly_Trigger")
                // daily at 00:44 UTC
                .withSchedule(
                        CronScheduleBuilder.cronSchedule("0 44 0 * * ?")
                                .inTimeZone(TimeZone.getTimeZone("UTC"))
                )
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "jobs.leaderboard-refresh-weekly", name = "enabled", havingValue = "true", matchIfMissing = false)
    public Trigger triggerLeaderboardRefreshWeeklyIntraday(@Qualifier("LeaderboardRefreshJob_Weekly") JobDetail job) {
        return TriggerBuilder.newTrigger().forJob(job)
                .withIdentity("LeaderboardRefreshJob_Weekly_Intraday_Trigger")
                .withSchedule(simpleSchedule().repeatForever().withIntervalInMinutes(10))
                .build();
    }

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
```

- [ ] **Step 2: Add config keys to `application.yml`**

Find, in the `jobs:` section:

```yaml
  player-spotlight:
    enabled: ${JOB_PLAYER_SPOTLIGHT:false}
```

Replace with:

```yaml
  player-spotlight:
    enabled: ${JOB_PLAYER_SPOTLIGHT:false}
  leaderboard-refresh-all-time:
    enabled: ${JOB_LEADERBOARD_REFRESH_ALL_TIME:false}
  leaderboard-refresh-monthly:
    enabled: ${JOB_LEADERBOARD_REFRESH_MONTHLY:false}
  leaderboard-refresh-weekly:
    enabled: ${JOB_LEADERBOARD_REFRESH_WEEKLY:false}
  leaderboard-refresh-season:
    enabled: ${JOB_LEADERBOARD_REFRESH_SEASON:false}
```

- [ ] **Step 3: Add config keys to `application-dev.yml`**

Find:

```yaml
  player-spotlight:
    enabled: true
```

Replace with:

```yaml
  player-spotlight:
    enabled: true
  leaderboard-refresh-all-time:
    enabled: true
  leaderboard-refresh-monthly:
    enabled: true
  leaderboard-refresh-weekly:
    enabled: true
  leaderboard-refresh-season:
    enabled: true
```

- [ ] **Step 4: Compile and run the full suite**

```bash
/Users/nerlich/workspace/luca/steam5/backend/gradlew -p /Users/nerlich/workspace/luca/steam5/backend test
```

Expected: `BUILD SUCCESSFUL`, all tests green (this also confirms Tasks 1-7 haven't broken anything else, e.g. `Steam5ApplicationTests#contextLoads`).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/org/steam5/config/QuartzConfig.java backend/src/main/resources/application.yml backend/src/main/resources/application-dev.yml
git commit -m "feat(config): schedule staggered leaderboard MV refresh jobs (nightly + 10min intraday for non-season)"
```

---

### Task 8: README documentation pass

**Files:**
- Modify: `README.md` (Query Performance Notes)

**Interfaces:**
- Consumes: everything decided in Tasks 1-7 (MV/index/job/config names).
- Produces: nothing — documentation only.

- [ ] **Step 1: Expand the "Query Performance Notes" section**

Find the bullet added back in Task 1:

```
- Leaderboard reads (`/api/leaderboard/all`, `/monthly`, `/weekly?floating=true`, `/season`) are backed by
  materialized views (`mv_leaderboard_all_time`, `mv_leaderboard_monthly`, `mv_leaderboard_weekly`,
  `mv_leaderboard_season` — see `backend/src/main/resources/db/mv-leaderboard-*.sql`). Like
  `idx_guesses_game_date`, these are **not** managed by Hibernate `ddl-auto` and must be applied manually
  against every environment (including each new dev DB). See the expanded write-up further down this
  section for the full rollout order and refresh-job configuration.
```

Replace it with:

```
- Leaderboard reads (`/api/leaderboard/all`, `/monthly`, `/weekly?floating=true`, `/season`) are served
  from materialized views (`mv_leaderboard_all_time`, `mv_leaderboard_monthly`, `mv_leaderboard_weekly`,
  `mv_leaderboard_season` — see `backend/src/main/resources/db/mv-leaderboard-*.sql`) instead of
  aggregating `guesses` on every request. This moves the expensive `GROUP BY steam_id` aggregation off
  the request path and gives every app instance a single shared, consistent read model — unlike
  per-instance Caffeine caching (`leaderboard-static`), which each instance populates independently and
  can disagree for up to its TTL after a restart or scale-out event. Caffeine still sits in front of the
  MV reads as a last-mile cache (10-minute TTL), so repeated identical requests avoid even the (now much
  cheaper) MV `SELECT`. The non-floating `/weekly` variant (previous Monday-Sunday week) is not MV-backed
  — its window doesn't match the MVs' rolling/current-window definitions — and remains a live
  `findAllBetween` query, as before.
  - Staleness is bounded by refresh cadence, not by the Caffeine TTL: each MV is refreshed by its own
    Quartz job (`LeaderboardRefreshJob`, one per type via `JobDataMap`, gated by
    `jobs.leaderboard-refresh-<type>.enabled`) on a nightly cron (00:40/00:42/00:44/00:46 UTC for
    all-time/monthly/weekly/season respectively, staggered after `seasons-finalizer` at 00:25 so season
    boundaries are settled) plus, for all-time/monthly/weekly only, an additional 10-minute interval
    trigger matching the `leaderboard-static` cache TTL. The season MV intentionally has no intraday
    trigger — its window depends on season rollover timing, not intraday freshness.
  - **Manual application required, in this order** (these MVs and their unique indexes are not managed
    by Hibernate `ddl-auto`, same as `idx_guesses_game_date`):
    1. Apply each `db/mv-leaderboard-*.sql` script against the target database (creates the view
       `WITH NO DATA` plus a `CREATE UNIQUE INDEX CONCURRENTLY` on `steam_id`, required for
       `REFRESH MATERIALIZED VIEW CONCURRENTLY`).
    2. Either run one manual `REFRESH MATERIALIZED VIEW mv_leaderboard_<type>;` per view before serving
       traffic, or enable the corresponding `jobs.leaderboard-refresh-<type>.enabled` flag and let
       `LeaderboardRefreshService` self-heal: it checks `pg_matviews.ispopulated` and automatically falls
       back to a plain (non-concurrent) `REFRESH` the first time, then uses `CONCURRENTLY` afterward.
       Until a view is populated, querying it raises a Postgres error — don't deploy the MV-backed read
       path ahead of this step.
    3. Only deploy/enable the MV-backed read path (`LeaderboardService`/`LeaderboardController`) after
       steps 1-2 have completed on the target database.
  - A hook to trigger a season-MV refresh directly from `SeasonService#finalizeSeason`/`#ensureSeasonForDate`
    was considered but intentionally not added: the season job's 00:46 UTC cron already runs after
    `seasons-finalizer` (00:25), so the extra coupling wasn't justified. Revisit if season rollover timing
    ever needs tighter (sub-cron-interval) correctness.
  - Validate the improvement empirically against the existing Grafana `steam5-postgres` dashboard (query
    latency/throughput on the `guesses` table) and `steam5-caches` dashboard (Caffeine hit rate for
    `leaderboard-static`) before/after rollout.
```

- [ ] **Step 2: Commit**

```bash
git add README.md
git commit -m "docs: document leaderboard MV rollout, refresh cadence, and MV-vs-Caffeine rationale"
```
