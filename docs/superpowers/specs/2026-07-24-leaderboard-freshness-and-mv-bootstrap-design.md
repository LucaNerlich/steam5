# Leaderboard Freshness Timestamp + MV Auto-Bootstrap — Design

## Goal

Two related changes to the leaderboard materialized-view (MV) feature shipped in PR #101:

1. **Auto-bootstrap the four leaderboard MVs and their unique indexes at application startup**, via a raw-JDBC `ApplicationRunner` (bypassing Hibernate's transactional `ddl-auto`, which is exactly why `CREATE INDEX CONCURRENTLY` couldn't run through it before). Removes the manual `psql` step from the rollout entirely.
2. **Show a small "last updated" paragraph below each MV-backed leaderboard** (weekly-floating, season, all-time — not `today`, not non-floating weekly, since those are live/not MV-backed), reflecting when the underlying materialized view was actually last refreshed. A new tiny Hibernate-managed table (`leaderboard_refresh_state`) tracks this, written by `LeaderboardRefreshService` after each successful refresh, exposed via an `X-Leaderboard-Refreshed-At` response header (mirroring the existing `X-Server-Timezone-Offset` pattern used for achievements), rendered client-side as a localized timestamp.

Combined with flipping the four `jobs.leaderboard-refresh-*.enabled` defaults to `true` in `application.yml`, the whole pipeline (create MV → populate → refresh → display freshness) becomes zero-touch from a fresh deploy.

## Architecture

- **`org.steam5.domain.LeaderboardType`** (new, promoted from `LeaderboardRefreshJob`'s current nested enum): `ALL_TIME, MONTHLY, WEEKLY, SEASON`. Single shared definition used by the job, the refresh service, and the new state entity.
- **`org.steam5.domain.LeaderboardRefreshState`** (new `@Entity`, ordinary Hibernate-managed table — no manual DDL): `leaderboardType` (`@Id`, `@Enumerated(EnumType.STRING)`) + `refreshedAt` (`OffsetDateTime`).
- **`org.steam5.repository.LeaderboardRefreshStateRepository`** (new): plain `JpaRepository<LeaderboardRefreshState, LeaderboardType>`, used by the refresh service (write/upsert) and the controller (read).
- **`org.steam5.service.LeaderboardRefreshService`** (modified): after each successful refresh, upserts `leaderboard_refresh_state` with `OffsetDateTime.now()`.
- **`org.steam5.config.LeaderboardMvBootstrapConfig`** (new): `@Bean ApplicationRunner`, following the exact style of the existing `DataSourcePoolLoggingConfig` (raw `DataSource` injection, no Hibernate/JPA involved). For each of the 4 MVs: read the corresponding `mv-leaderboard-*.sql` classpath resource, split into its two statements (`CREATE MATERIALIZED VIEW ... WITH NO DATA` and `CREATE UNIQUE INDEX CONCURRENTLY ...`), check existence via `pg_matviews`/`pg_indexes`, execute whichever is missing over a raw `Connection` (autocommit, no Spring transaction). Gated by `app.leaderboard-mv.bootstrap.enabled` (default `true`). Logs each action; any `SQLException` is logged at ERROR but does not fail application startup.
- **`org.steam5.web.LeaderboardController`** (modified): adds the `X-Leaderboard-Refreshed-At` header (ISO-8601, or omitted if no state row exists yet) to the weekly(`floating=true`)/season/all-time responses only.
- **Frontend `LeaderboardTable.tsx`** (modified): a custom SWR fetcher captures the header (mirrors achievements' `serverOffsetMinutes` pattern) and the component renders `Intl.DateTimeFormat(undefined, {dateStyle:'medium', timeStyle:'short'})`-formatted text below the average-points line, only for `season`/`all`/`weekly-floating` modes.
- **`src/lib/leaderboard.ts`** (modified): a small pure `formatRefreshedAt(iso: string): string` helper, unit-tested via Vitest.

## Data Flow

Startup: bootstrap runner creates any missing MVs/indexes (`WITH NO DATA`) → refresh jobs (now enabled by default) populate them on first fire, self-healing via the existing `pg_matviews.ispopulated` check → each successful refresh writes `leaderboard_refresh_state` → a leaderboard request reads the MV + the state row → response carries data + `X-Leaderboard-Refreshed-At` header → Caffeine caches the whole `ResponseEntity` (headers included) until the refresh job's post-refresh eviction, so the cached header and the MV data never disagree → frontend renders the localized timestamp from that header.

## Error Handling

- Bootstrap runner: any DDL failure is logged with full exception detail; the app still boots (matches the existing non-fatal posture of this codebase's background jobs). `CREATE INDEX CONCURRENTLY` can leave an `INVALID` index behind if the app crashes mid-build — not auto-recovered (matches Postgres's own limitation); worth a one-line README callout, not code-level handling (YAGNI).
- If `leaderboard_refresh_state` has no row yet for a type (fresh boot, before any refresh has run), the controller omits the header; the frontend hides the "Last updated" paragraph rather than showing a broken/blank timestamp.

## Testing

- Backend: `LeaderboardRefreshServiceTest` extended to verify the upsert call; new `LeaderboardMvBootstrapConfigTest` using mocked `DataSource`/`Connection`/`Statement`/`ResultSet` to verify the create-if-missing / skip-if-exists branching; `LeaderboardControllerTest` extended to verify the header is present for the three MV-backed endpoints and absent for `today`/non-floating weekly.
- Frontend: Vitest unit test for `formatRefreshedAt`. No component-level test for `LeaderboardTable` (no existing precedent for that in this codebase).

## Decisions Made (via brainstorming)

- Scope: only weekly-floating/season/all-time get the "last updated" paragraph — not `today` (always live) or non-floating weekly (also live).
- Data source/delivery: new Postgres table (not in-memory) + response header (not a body-shape change) — shared/consistent across app instances, survives restarts, follows the existing achievements-header precedent.
- Bootstrap gating: config flag `app.leaderboard-mv.bootstrap.enabled`, default `true` (escape hatch for ops, matches the `jobs.*.enabled` convention).
- Refresh job defaults: `jobs.leaderboard-refresh-*.enabled` flip from `false` to `true` in `application.yml`, completing the zero-touch rollout goal.
