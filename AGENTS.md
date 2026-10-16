# Agents

## Cursor Cloud specific instructions

### Architecture

Steam5 is a monorepo with two independent sub-projects:

- **Backend** (`backend/`): Spring Boot 4.1.1, Java 21, Gradle 9.8.1, PostgreSQL
- **Frontend** (`frontend/`): Next.js 16, React 19, TypeScript, pnpm 10.15.1, Node 22

Standard commands are documented in `README.md` and `.junie/guidelines.md`.

### Services

| Service | Port | Start command |
|---------|------|---------------|
| PostgreSQL | 5432 | `pg_ctlcluster 16 main start` |
| Backend | 8080 | `cd backend && ./gradlew bootRun` |
| Frontend | 3000 | `cd frontend && pnpm dev` |
| Actuator (dev) | 8081 | Starts automatically with backend |

### Database setup (one-time)

PostgreSQL must be running before the backend starts. Database `steam5_db` with user `steam5_user` / password `steam5_password` must exist. See `backend/setup-local-db.sql` or run:

```
sudo -u postgres psql -c "CREATE DATABASE steam5_db;"
sudo -u postgres psql -c "CREATE ROLE steam5_user WITH LOGIN PASSWORD 'steam5_password' CREATEDB SUPERUSER;"
sudo -u postgres psql -c "GRANT ALL PRIVILEGES ON DATABASE steam5_db TO steam5_user;"
sudo -u postgres psql -d steam5_db -c "GRANT ALL ON SCHEMA public TO steam5_user;"
```

### Backend `.env` file

The backend loads env vars from `backend/.env` via spring-dotenv. Copy from `backend/.env.example` and fill in values. For local dev without Steam API access, dummy values work (Steam API jobs will fail gracefully but the app runs):

```
STEAM_API_KEY=dummy-dev-key
ADMIN_API_TOKEN=dev-admin-token
AUTH_JWT_SECRET=dev-jwt-secret-change-me-32-bytes-minimum-length
SPRING_PROFILE=dev
SPRING_DATASOURCE_PASSWORD=steam5_password
```

### Database migrations (Flyway)

**Whenever a change affects the database schema (new/changed entities, columns, tables, indexes or
constraints), add a new migration `backend/src/main/resources/db/migration/V<N>__short_description.sql`.**
Production runs `ddl-auto: validate` (`application-coolify.yml`) and only Flyway changes its schema —
a schema change that isn't a migration either fails startup or silently never reaches production.

- Never edit or delete an already-applied migration (including `V1__baseline.sql`); Flyway verifies
  checksums and startup fails when one changed. Add the next version instead.
- `V1__baseline.sql` is the captured production schema and only runs on empty databases. Existing
  databases are baselined at version 1 and only receive higher-numbered migrations.
- Migrations run automatically on backend startup in every profile — nothing to apply by hand. Start
  the backend locally (`./gradlew bootRun`) and it applies the new migration.
- The one-off scripts in `backend/src/main/resources/db/*.sql` (e.g. `all-indexes.sql`,
  `flyway-legacy-history-reset.sql`) remain manual tools; do not convert them into migrations.

### Gotchas

- The frontend redirects `/` to `/review-guesser/1` (HTTP 308). Use `curl -L` when testing.
- Actuator (port 8081) requires authentication via Spring Security in the dev profile.
- All Quartz jobs are enabled in the `dev` profile (`application-dev.yml`). Jobs that call the Steam API will log errors with a dummy API key but do not block startup.
- Hibernate runs `ddl-auto: update` in dev, but production validates the schema (`ddl-auto: validate`). Every schema change ships as a new Flyway migration — see "Database migrations (Flyway)" above.
- No ESLint config exists for the frontend; use `pnpm build` as the lint/type check.
- Node 22 is installed via nvm. Source nvm before running frontend commands: `export NVM_DIR="$HOME/.nvm" && [ -s "$NVM_DIR/nvm.sh" ] && . "$NVM_DIR/nvm.sh"`
