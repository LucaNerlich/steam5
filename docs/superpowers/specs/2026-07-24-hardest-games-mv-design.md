# Hardest Games Materialized View — Design

## Goal

Add a fifth materialized view, `mv_hardest_games`, backing `GET /api/stats/game/hardest` (currently a live, full-table-scan CTE query gated only by a 1-hour Caffeine cache), refreshed once daily (no intraday cadence needed). Reuse the existing leaderboard-MV infrastructure (bootstrap, refresh service, refresh job, freshness header/UI) rather than forking a parallel implementation.

## Architecture

- **`mv_hardest_games`**: reproduces `GuessRepository#findHardestGames(limit, minPlayers)`'s query exactly (same two CTEs — `wrong_counts`/`top_wrong` — same regex-based too-high/too-low comparison, same `DISTINCT ON` most-common-wrong-bucket logic), with `minPlayers` fixed at `5` (the only value ever passed today, from `StatisticsService#getHardestGames`) and no `LIMIT` — the view materializes every game with ≥5 players, ordered by avg score ascending; the app still applies `limit` in Java exactly as it does today.
- Every non-grouped joined column in this query is already wrapped in an aggregate (`MAX(...)`/`COALESCE(MAX(...))`), so — unlike the original 4 leaderboard MVs before their fix — it never relies on Postgres's functional-dependency-on-primary-key GROUP BY optimization. It still has an unavoidable table-level dependency on `guesses`/`steam_app_index`, so it's added to the `pg_restore --clean` drop list in `leaderboard-mv-maintenance.sql`.
- **Reuses the existing generalized machinery**: `LeaderboardType` gains `HARDEST_GAMES`; `LeaderboardMvRepository` gains a `HardestGameMvRow` projection + `findHardestGames()` read + refresh-pair methods; `LeaderboardRefreshService` gains `refreshHardestGames()` via the same private `refresh()` helper (advisory lock, populated-check, state-recording); `LeaderboardRefreshJob` gets a 5th `@Bean JobDetail` + switch case; `LeaderboardMvBootstrapConfig` gets a 5th `MvDefinition` entry (create-if-missing + populate-if-unpopulated for free).
- **Scheduling**: one nightly-only cron trigger (00:48 UTC, staggered after `season`'s 00:46), no intraday trigger — "once a day is enough" per explicit requirement. `jobs.leaderboard-refresh-hardest-games.enabled`, default `true`.
- **Read path**: `StatisticsService#getHardestGames(limit)` swaps its data source from the live `guessRepository.findHardestGames(limit, 5)` to `leaderboardMvRepository.findHardestGames()` + `.limit(limit)` in Java; the deception-rate/direction mapping is untouched. `GuessRepository#findHardestGames`/`HardestGameRow` become dead code and are deleted (matches the earlier `aggregateAllTimeStats()` precedent). The existing `stats-hourly` Caffeine cache annotation stays as the last-mile cache on top of the MV.
- **Freshness UI**: `StatisticsController#hardestGames` gets the same `X-Leaderboard-Refreshed-At` header treatment as `LeaderboardController` (new `LeaderboardRefreshStateRepository` dependency + a local `withRefreshedAtHeader` helper — duplicated rather than shared, per this codebase's YAGNI stance until a third occurrence justifies extracting one). `app/api/stats/game/hardest/route.ts` gets the same header-forwarding-through-proxy fix just applied to the other four leaderboard routes. `HardestGamesTable.tsx` gets the same wrapped-SWR-fetcher + `formatRefreshedAt` "Last updated" rendering as `LeaderboardTable.tsx`.

## Testing

Neither `StatisticsService` nor `StatisticsController` has any existing test file. New, narrowly-scoped test files cover only the changed/new behavior (`getHardestGames`'s MV-backed data source + limit truncation; the new header on `hardestGames()`) — not a backfill of the other ~15 untested methods on `StatisticsService`, matching how `SeasonFinalizerJobTest` was added from scratch earlier for just its own new behavior. `LeaderboardRefreshServiceTest`, `LeaderboardRefreshJobTest`, and `LeaderboardMvBootstrapConfigTest` are extended for the 5th type (the bootstrap test's expected statement counts change from 4-MV to 5-MV multiples).

## Decisions Made (via brainstorming)

- Full parity with the other 4 leaderboards, including the "Last updated" freshness UI (not just the MV + refresh job).
- Reuse `LeaderboardType`/`LeaderboardMvRepository`/`LeaderboardRefreshService`/`LeaderboardRefreshJob` rather than forking parallel infrastructure for one MV, accepting a minor naming imprecision ("Leaderboard*" now also covers a per-game stat, not just per-player leaderboards) in exchange for not duplicating the isPopulated/advisory-lock/state-tracking machinery.
