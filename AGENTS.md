# Agents

## Cursor Cloud specific instructions

### Architecture

Steam5 is a monorepo with two independent sub-projects:

- **Backend** (`backend/`): Spring Boot 4.0.5, Java 21, Gradle 9.4.1, PostgreSQL
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
```

### Gotchas

- The frontend redirects `/` to `/review-guesser/1` (HTTP 308). Use `curl -L` when testing.
- Actuator (port 8081) requires authentication via Spring Security in the dev profile.
- All Quartz jobs are enabled in the `dev` profile (`application-dev.yml`). Jobs that call the Steam API will log errors with a dummy API key but do not block startup.
- Backend tests: 2 tests (`AuthControllerTest.validate_token_ok_and_invalid` and `LeaderboardControllerTest.allTime_returnsAggregatedLeaders`) are pre-existing failures due to test/production code drift. The remaining 17 tests pass.
- Flyway migrations and Hibernate `ddl-auto: update` both run on startup. Schema is auto-managed.
- No ESLint config exists for the frontend; use `pnpm build` as the lint/type check.
- Node 22 is installed via nvm. Source nvm before running frontend commands: `export NVM_DIR="$HOME/.nvm" && [ -s "$NVM_DIR/nvm.sh" ] && . "$NVM_DIR/nvm.sh"`
