## Steam5 — Review Guesser (Monorepo)

Daily guessing game: five Steam games per day, guess each game’s review-count bucket. Monorepo contains a Spring Boot
backend and a Next.js frontend.

### Backend APIs used

- IStoreService/GetAppList: `https://steamapi.xpaw.me/#IStoreService/GetAppList`
- Steam Storefront: `https://store.steampowered.com/api/appdetails`
- Steam Reviews: `https://store.steampowered.com/appreviews`

### Tech stack

- Java 21, Spring Boot 3.5
- Gradle (wrapper included)
- PostgreSQL, JPA/Hibernate
- Quartz for scheduled jobs
- Actuator for health/metrics

---

## Local development

### Prerequisites

- Java 21 (required)
- PostgreSQL 16+ (local or Docker)

### Database setup

Default JDBC URL in `backend/src/main/resources/application.yml` is:

```
jdbc:postgresql://localhost:5432/postgres
```

Create database and a least-privileged user:

```sql
-- From psql connected as a superuser (e.g. postgres):
CREATE DATABASE postgres;
CREATE ROLE steam5_user WITH LOGIN PASSWORD 'steam5_password';
GRANT CONNECT ON DATABASE postgres TO steam5_user;
\c steam5
GRANT USAGE ON SCHEMA public TO steam5_user;
GRANT CREATE, USAGE ON SCHEMA public TO steam5_user;
```

Alternatively, run Postgres via Docker:

```bash
docker run --name steam5-pg -e POSTGRES_DB=postgres -e POSTGRES_USER=steam5_user -e POSTGRES_PASSWORD=steam5_password -p 5432:5432 -d postgres:16
```

### Export and Import Backup

1. Via IDEA, right click "steam5_db" in database browser
2. Export via pg_dump
3. Export as tar and with "copy" statement
4. Rename generated .sql file to .sql.gz
5. Upload to coolify "Import Backup"
6. Add `--clean` to import command
7. Run import

If first time, login to container and setup database and role, via above sql steps.

### Configure environment (overrides)

You can override properties with environment variables:

- `SPRING_DATASOURCE_URL` (e.g. `jdbc:postgresql://localhost:5432/steam5`)
- `SPRING_DATASOURCE_USERNAME` (default `steam5_user`)
- `SPRING_DATASOURCE_PASSWORD` (default `steam5_password`)
- `SERVER_PORT` (default `8080`)

The `dev` profile is active by default. See `backend/src/main/resources/application.yml`.

### Run the backend

- Windows (PowerShell):
  ```powershell
  cd backend; .\gradlew.bat bootRun
  ```
- macOS/Linux:
  ```bash
  cd backend && ./gradlew bootRun
  ```

Run tests:

```bash
cd backend && ./gradlew test
```

---

## Scheduled jobs

Quartz jobs (see `backend/src/main/java/org/steam5/job`):

- `SteamAppListJob`, `SteamAppReviewsJob`, `SteamAppDetailJob`: periodic ingestion and refresh
- `ReviewGameStateJob`: generates daily picks (once per day)
- `BlurhashScreenshotsJob`, `BlurhashAvatarJob`: compute BlurHash placeholders asynchronously

Jobs can also be triggered ad-hoc by the application (e.g., after daily picks generation or user profile update).
Respect rate limits; on any Steam 429 the jobs abort early.

---

## Backend REST API (selected)

- `GET /api/review-game/today` and `/today/details`: daily picks and details
- `POST /api/review-game/guess`: submit a guess
- `GET /api/review-game/buckets`: bucket labels for UI
- `GET /api/leaderboard/today` and `/leaderboard`: leaderboards
- Auth: `/api/auth/steam/*` (OpenID), `/api/auth/me`, `/api/auth/logout`
- Actuator: `/actuator/*` (includes `/actuator/quartz` in dev)

Security: token-based auth via Steam login. See `frontend/app/api/auth/*` and `backend/web/AuthController`.

---

## Actuator (dev, on port 8081)

- Index
    - [Actuator root](http://localhost:8081/actuator): lists all exposed endpoints.

- Health
    - [Health](http://localhost:8081/actuator/health): overall and detailed status.

- Metrics (JSON)
    - [Metrics index](http://localhost:8081/actuator/metrics): all metric names.
    - Cache metrics (Caffeine with `recordStats()`):
        - [cache.requests (all)](http://localhost:8081/actuator/metrics/cache.requests)
        - Per cache name (examples):
            - Hits/misses: `GET /actuator/metrics/cache.gets?tag=cache:review-game`
            - Requests: `GET /actuator/metrics/cache.requests?tag=cache:one-day`
            - Puts: `GET /actuator/metrics/cache.puts?tag=cache:review-game`
            - Evictions: `GET /actuator/metrics/cache.evictions?tag=cache:review-game`
            - Size: `GET /actuator/metrics/cache.size?tag=cache:review-game`
        - Tip: add `&tag=cacheManager:cacheManager` if you have multiple managers.
    - HTTP server metrics:
        - `GET /actuator/metrics/http.server.requests?tag=uri:/api/review-game/today`

- Prometheus (text)
    - [Prometheus scrape](http://localhost:8081/actuator/prometheus)
    - Search for series like `cache_gets_total`, `cache_requests_total`, `cache_evictions_total` with tags
      `cache="review-game"` etc.

- Environment and logging
    - [Environment](http://localhost:8081/actuator/env)
    - [Loggers](http://localhost:8081/actuator/loggers) (lists all loggers and their levels)

- Beans
    - [Beans](http://localhost:8081/actuator/beans)

- Quartz (scheduler)
    - [Quartz index](http://localhost:8081/actuator/quartz)
    - [Jobs](http://localhost:8081/actuator/quartz/jobs)
    - [Triggers](http://localhost:8081/actuator/quartz/triggers)

Notes

- You’ll only see cache metrics after endpoints using `@Cacheable` are exercised.
- Actuator runs on its own management port (`MANAGEMENT_SERVER_PORT`, default `8081`) and is protected
  by HTTP Basic auth. In dev the credentials default to `metrics` / `metrics`; `/actuator/health` is
  the only endpoint that remains publicly reachable. In production (coolify) the same scheme applies and
  the defaults **must** be overridden via `METRICS_USERNAME` / `METRICS_PASSWORD` — the backend logs a
  startup `WARN` if the dev defaults are still in use. To narrow exposure further, adjust
  `SecurityConfig` / `EndpointRequest`.

---

## Monitoring (Prometheus + Grafana)

A self-contained Prometheus + Grafana stack lives in [`monitoring/`](monitoring/README.md) and scrapes
the backend's `/actuator/prometheus` endpoint over HTTP basic auth.

- **Stack**: Prometheus 3.5.1, Grafana 12.3.1 (image tags pinned in `monitoring/.env.example`).
- **Dashboards**: 5 provisioned dashboards (JVM, HTTP server, HikariCP, Caches, Quartz jobs).
- **Local quick start** (with the backend already running on `MANAGEMENT_SERVER_PORT=8081`):

  ```bash
  cd monitoring
  cp .env.example .env
  docker compose --env-file .env up -d
  ```

- **URLs**: Prometheus at <http://localhost:9090>, Grafana at <http://localhost:3001>
  (default credentials `admin` / `admin` — change in `.env`).

See [`monitoring/README.md`](monitoring/README.md) for the full reference, environment variable table,
cross-platform notes (macOS vs. Linux `host.docker.internal`), Coolify production deployment guide,
and troubleshooting.

---

## Frontend (Next.js)

- Next 15, App Router, TypeScript
- Local fonts via `next/font/local` (Monaspace Krypton & Neon)
    - To keep payload small we only ship Regular (400) and Bold (700) for each
    - Neon is not preloaded; toggled via the UI will load it on demand
- BlurHash placeholders for screenshots and avatars
- Image host allowlist in `next.config.ts`

Dev:

```bash
cd frontend
npm i
npm run dev
```

## Contributing / Dev workflow

- Create a migration for any schema change (never change past migrations).
- Keep entities and repositories aligned with the schema.
- Add tests for the ingest job and API controllers.

## Query Performance Notes

- `guesses` date-range queries (for example `findAllBetween`, `findSeasonStats`, `findSeasonDates`) rely on `idx_guesses_game_date`.
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
- Profile history lookup uses `(steam_id, game_date, round_index)` via `findBySteamIdOrderByGameDateDescRoundIndexAsc`.
- `SteamAppReviewsRepository` random-pick methods use a two-phase CTE + `NOT EXISTS` pattern to avoid random sorting on the full table.
- Optional DBA-only index for large review datasets:
  ```sql
  CREATE INDEX idx_reviews_eligible ON steam_app_reviews (app_id)
  WHERE (total_positive + total_negative) > 0;
  ```
  Add this as a migration when schema management is centralized.
- `GuessRepository` multi-scan CTE methods (`findUsersByPerfectDays*`, `findUsersByDailyTimeDiff*`) are currently service-cached; if data volume grows, prioritize window-function rewrites.
- `leaderboardAllTime` is an inherent full-table aggregation and should be monitored as table size grows; caching reduces runtime pressure.

---

## License

See `LICENSE`.
