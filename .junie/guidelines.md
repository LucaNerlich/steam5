# Project Guidelines — steam5

This document tells Junie (and contributors) how this repository is organized, how to run/build/test locally, and when
Junie should run checks before submitting fixes.

<!-- TOC -->
* [Project Guidelines — steam5](#project-guidelines--steam5)
  * [Repository overview](#repository-overview)
  * [Technology matrix](#technology-matrix)
  * [Environment and configuration](#environment-and-configuration)
  * [Local setup](#local-setup)
    * [Backend — run, test, build](#backend--run-test-build)
    * [Frontend — run, build](#frontend--run-build)
  * [Running the full stack locally](#running-the-full-stack-locally)
  * [How Junie should verify changes](#how-junie-should-verify-changes)
  * [Coding conventions](#coding-conventions)
  * [Troubleshooting](#troubleshooting)
  * [Useful files](#useful-files)
<!-- TOC -->

## Repository overview

Root: `/mnt/workspace/luca/steam5`

- `backend/` — Spring Boot 3.5 (Java 21, Gradle Kotlin DSL). PostgreSQL for persistence. Uses `.env` via `spring-dotenv`
  to load environment variables in dev.
- `frontend/` — Next.js 16 with React 19, TypeScript. Node 22 runtime, package manager `pnpm@10.15.1`.
- `Dockerfile.coolify` — containerization for deployment (Coolify).
- `steam5.log` — runtime log (if produced in root).
- `.junie/` — Junie configuration and this guidelines file.

## Technology matrix

- Backend
    - Java 21, Spring Boot 3.5.6, Gradle
    - Testing: JUnit Platform (configured via `useJUnitPlatform()`)
    - Database: PostgreSQL (JDBC)
    - Notable libs: Spring Security, OAuth2 Client, Quartz, Caffeine cache, JJWT, Lombok
- Frontend
    - Next.js `^16`, React `^19`, TypeScript `^5.9`
    - Node `22`, npm `11`, pnpm `10.15.1` (see `frontend/package.json`)

## Environment and configuration

- Profiles: `spring.profiles.active` defaults to `dev` (`SPRING_PROFILE` can override). Server default port `8080` (
  `SERVER_PORT`).
- Database defaults (dev):
    - URL: `jdbc:postgresql://localhost:5432/steam5_db`
    - Username: `steam5_user`, Password: `steam5_password`
- Redirect base for auth flows: `REDIRECT_BASE` (defaults to `http://localhost:3000`).
- Review game settings: `LOW_PERCENTILE`, `HIGH_PERCENTILE`, `MIN_REVIEWS_FRESH_DAYS`.
- Batch/job toggles (all default to disabled unless overridden):
    - `JOB_INGEST_LIST`, `JOB_INGEST_REVIEWS`, `JOB_INGEST_DETAILS`,
    - `JOB_GENERATE_REVIEW_STATE`, `JOB_BLURHASH`, `JOB_BLURHASH_AVATAR`,
    - `JOB_REVIEWS_REFRESH` and `JOB_REVIEWS_REFRESH_LIMIT`
- `.env` support: `backend` loads variables from a `.env` file (see `backend/.env.example`).

## Local setup

Prerequisites

- Java 21 (for backend)
- Node 22 and pnpm 10.15.1 (for frontend)
- PostgreSQL 14+ accessible locally (or a JDBC URL to a remote instance)

Database (dev)

- Create a database and credentials matching the defaults above, or set `JDBC_URL`, `SPRING_PROFILE`, etc., in
  `backend/.env`.
- Hibernate is configured with `ddl-auto: update` for dev; schema will evolve automatically.

### Backend — run, test, build

Commands (run inside `backend/`):

- Run app (dev):
    - Unix/macOS: `./gradlew bootRun`
    - Windows: `gradlew.bat bootRun`
- Run tests: `./gradlew test`
- Build jar: `./gradlew build`

Notes

- `.env` variables in `backend/` are auto-loaded thanks to `me.paulschwarz:spring-dotenv`.
- Server starts on `http://localhost:8080` by default.

### Frontend — run, build

Commands (run inside `frontend/`):

- Install: `pnpm install`
- Dev server: `pnpm dev` (Next.js on `http://localhost:3000`)
- Production build: `pnpm build`
- Start production server: `pnpm start`

## Running the full stack locally

1) Start the backend (port 8080). Ensure DB is reachable and env vars are set via `backend/.env` or your shell.
2) Start the frontend (port 3000). If the frontend needs the backend URL, configure it via the appropriate environment
   variable or config in the frontend (not enforced by this file; check component usage or API clients if needed).

## How Junie should verify changes

- Documentation-only changes (e.g., Markdown, comments):
    - Do not build or run tests.
- Backend code/config changes:
    - Run backend unit tests: `backend: ./gradlew test`.
    - For changes affecting startup/configuration, consider building: `backend: ./gradlew build`.
- Frontend code changes:
    - There are no frontend tests configured in `package.json`; verify build compiles: `frontend: pnpm build` (and fix
      type errors if any).
- Cross-cutting changes (affecting both apps):
    - Build both: `backend: ./gradlew build` and `frontend: pnpm build`.
- Do not run long end-to-end flows unless explicitly requested.

## Coding conventions

- Backend (Java):
    - Java 21 language features are allowed.
    - Spring Boot idioms, layered architecture (controller/service/repository).
    - Lombok is used; prefer Lombok annotations where already present.
    - Formatting: follow existing style (4-space indentation, Gradle Kotlin DSL in `build.gradle.kts`).
    - Tests: JUnit 5 (Platform) — place under `src/test/java`.
- Frontend (TypeScript/React):
    - Next.js 16, React 19 functional components and hooks.
    - TypeScript 5.9; keep types explicit for public functions/components.
    - Use SWR for data fetching where already established.
    - Follow existing code patterns and file organization; keep components small and focused.

## Troubleshooting

- Backend fails to start: verify PostgreSQL is running and credentials match `application.yml`/env. Check `JDBC_URL` and
  driver.
- CORS/auth redirects: ensure `REDIRECT_BASE` is correct for your frontend URL.
- Jobs not running: confirm `jobs.*.enabled` flags via environment variables listed above.

## Useful files

- `backend/src/main/resources/application.yml` — canonical configuration reference.
- `backend/.env.example` — starting point for local env.
- `backend/build.gradle.kts` — dependencies and plugin versions.
- `frontend/package.json` — scripts, engines, and package manager.
