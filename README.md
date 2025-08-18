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
- Flyway for DB migrations
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

## Database & migrations

Flyway is enabled and will apply SQL files in `backend/src/main/resources/db/migration` on startup.

- Initial migration: `V1__create_initial_schema.sql` (to be filled with the tables below).
- Naming: `V{N}__{snake_case_description}.sql` (e.g., `V2__add_user_tables.sql`).

---

## Scheduled jobs

Quartz jobs (see `backend/src/main/java/org/steam5/job`):

- `SteamAppListJob`, `SteamAppReviewsJob`, `SteamAppDetailJob`: periodic ingestion and refresh
- `ReviewGameStateJob`: generates daily picks (once per day)
- `BlurhashScreenshotsJob`, `BlurhashAvatarJob`: compute BlurHash placeholders asynchronously

Jobs can also be triggered ad-hoc by the application (e.g., after daily picks generation or user profile update).
Respect rate limits; on any Steam 429 the jobs abort early.

---

## Daily picks: how the 5 games are chosen

- Lock and reuse
    - If today already has picks in `review_game_pick`, return them.
    - Otherwise acquire a per-day DB lock via `daily_pick_lock`. If not acquired, we briefly poll (~2s) and reuse the
      other worker’s picks.

- Parameters and thresholds
    - Exclude previously picked appIds since `review.game.do-not-repeat-days` (default 3650); fall back to “include all”
      when needed.
    - Compute low/high review thresholds from existing `steam_app_reviews` using percentiles
      `review.game.low-percentile` / `high-percentile`.
    - Bucket labels come from `review.game.bucket-boundaries`.

- Candidate selection
    - Use random queries that exclude recent picks and excluded apps:
        - LOW: one from ≤ lowThreshold (else retry with include-all window)
        - HIGH: one from ≥ highThreshold (else retry include-all)
        - ANY: fill remaining slots from any; if needed retry include-all
    - For each candidate appId, validate by fetching details. On failure it’s recorded in `excluded_app`. On Steam 429
      we abort the whole run (no exclusion), to respect rate limits.
    - Eliminate duplicates with an in-memory set.

- Persist and post-processing
    - Save to `review_game_pick` for today.
    - For each picked appId:
        - Conditionally refresh reviews if stale: when `steam_app_reviews.updated_at` < now −
          `review.game.min-reviews-fresh-days` (default 7), otherwise skip.
        - Refresh details (best-effort).
        - Trigger screenshot BlurHash computation asynchronously.
    - Clear the `review-game` cache so the FE loads fresh data.

- Concurrency and safety
    - The per-day DB lock prevents duplicate generation across job and endpoint.
    - Any Steam 429 aborts generation to avoid hammering the API.

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

---

## License

See `LICENSE`.
