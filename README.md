## Steam5 — Review Guesser (Backend)

Spring Boot backend for a daily game that shows five random Steam games. Players guess each game's review count; results
are revealed after submitting guesses.

### APIs

- https://steamapi.xpaw.me/#IStoreService/GetAppList

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
jdbc:postgresql://localhost:5432/steam5
```

Create database and a least-privileged user:

```sql
-- From psql connected as a superuser (e.g. postgres):
CREATE DATABASE steam5;
CREATE ROLE steam5_user WITH LOGIN PASSWORD 'steam5_password';
GRANT CONNECT ON DATABASE steam5 TO steam5_user;
\c steam5
GRANT USAGE ON SCHEMA public TO steam5_user;
GRANT CREATE, USAGE ON SCHEMA public TO steam5_user;
```

Alternatively, run Postgres via Docker:

```bash
docker run --name steam5-pg -e POSTGRES_DB=steam5 -e POSTGRES_USER=steam5_user -e POSTGRES_PASSWORD=steam5_password -p 5432:5432 -d postgres:16
```

### Configure environment (overrides)

You can override properties with environment variables:

- `SPRING_DATASOURCE_URL` (e.g. `jdbc:postgresql://localhost:5432/steam5`)
- `SPRING_DATASOURCE_USERNAME` (default `steam5_user`)
- `SPRING_DATASOURCE_PASSWORD` (default `steam5_password`)
- `SERVER_PORT` (default `8080`)

The `dev` profile is active by default. See `backend/src/main/resources/application.yml`.

### Run the app

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

## Scheduled job: fetch and store games

We will run a Quartz job daily to ingest games and prepare the day’s set.

Planned job: `SteamGameIngestJob`

- Schedule: daily around 00:05 UTC (configurable)
- Steps:
    1. Fetch Steam app list and/or candidate set.
    2. Sample/choose five eligible games (exclude DLC, unreleased, duplicates, etc.).
    3. Fetch metadata and review counts per app via Steam Storefront API.
    4. Upsert into `game` and insert a `game_review_snapshot` row for each.
    5. Create a `daily_set` for the date and five `daily_set_game` rows.

Suggested configuration (add to `application.yml` as needed):

```yaml
steam:
  ingest:
    cron: "5 0 * * *"   # every day at 00:05 UTC
    country: "US"       # for store API localization
    language: "en"
```

Notes:

- Steam Storefront details endpoint:
  `https://store.steampowered.com/api/appdetails?appids={APPID}&cc={COUNTRY}&l={LANG}`
- Respect Steam’s terms/rate limits; add retry/backoff.

---

## Planned REST API (first iteration)

- `GET /api/v1/daily`
    - Returns the five games for today: `[{ appId, name, headerImage, slot }]`.
- `POST /api/v1/daily/{date}/guess`
    - Body: `{ guesses: [{ appId, guessedReviewCount }] }`
    - Returns stored guesses and a simple score diff summary.
- `GET /api/v1/daily/{date}/result`
    - Returns actual counts for the day and computed deltas.
- Actuator: `GET /actuator/health`, `GET /actuator/metrics`, `GET /actuator/flyway`, etc.

Security/auth will be added later; initial endpoints can be open for development.

---

## Contributing / Dev workflow

- Create a migration for any schema change (never change past migrations).
- Keep entities and repositories aligned with the schema.
- Add tests for the ingest job and API controllers.

---

## License

See `LICENSE`.
