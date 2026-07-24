# Leaderboard Freshness Timestamp + MV Auto-Bootstrap Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Auto-create the four leaderboard materialized views (and their unique indexes) at application startup instead of requiring a manual `psql` step, and show a small "last updated" paragraph below each MV-backed leaderboard (weekly-floating, monthly, season, all-time) reflecting when that view was actually last refreshed.

**Architecture:** A new raw-JDBC `ApplicationRunner` (`LeaderboardMvBootstrapConfig`) reads the existing `mv-leaderboard-*.sql` files as classpath resources, checks `pg_matviews`/`pg_indexes` for existence, and creates whatever's missing over an autocommit `Connection` — bypassing Hibernate's transactional `ddl-auto`, which is why `CREATE INDEX CONCURRENTLY` couldn't run through it before. A new ordinary (Hibernate-managed) table, `leaderboard_refresh_state`, tracks one `refreshedAt` timestamp per `LeaderboardType` (a newly-promoted shared enum), written by `LeaderboardRefreshService` after every successful refresh. `LeaderboardController` reads that table and adds an `X-Leaderboard-Refreshed-At` response header (mirroring the existing `X-Server-Timezone-Offset` pattern used for achievements) to the four MV-backed endpoints. The frontend's `LeaderboardTable` captures that header via a wrapped SWR fetcher and renders a localized "Last updated" line for the three MV-backed *pages* (weekly-floating, season, all-time — there's no `/monthly` page, only the API endpoint). Flipping `jobs.leaderboard-refresh-*.enabled` to default `true` in `application.yml` completes the zero-touch rollout: create → populate → refresh → display freshness, no manual steps anywhere.

**Tech Stack:** Spring Boot 4.1 / Java 21 backend (Gradle), raw JDBC (`javax.sql.DataSource`), Spring Data JPA, JUnit 5 + Mockito; Next.js/TypeScript frontend, SWR, Vitest.

## Global Constraints

- **Design spec:** `docs/superpowers/specs/2026-07-24-leaderboard-freshness-and-mv-bootstrap-design.md` — read it if anything below is ambiguous.
- **Test/build commands** (`cd` triggers a permission issue in some sandboxed sessions — always use the `-p` form):
  - Backend fast loop: `/Users/nerlich/workspace/luca/steam5/backend/gradlew -p /Users/nerlich/workspace/luca/steam5/backend test --tests "<FQCN>"`.
  - Backend full suite: `/Users/nerlich/workspace/luca/steam5/backend/gradlew -p /Users/nerlich/workspace/luca/steam5/backend test`. Expect exactly one pre-existing, unrelated failure: `Steam5ApplicationTests#contextLoads` (`jakarta.websocket.server.ServerContainer` bean-creation issue, present before this work started). Any other failure is a real regression.
  - Frontend: `npm --prefix /Users/nerlich/workspace/luca/steam5/frontend test` (runs `vitest run`).
- **`LeaderboardType`** moves from a nested enum inside `LeaderboardRefreshJob` to `org.steam5.domain.LeaderboardType` with the same four constants, same names: `ALL_TIME, MONTHLY, WEEKLY, SEASON`. Every consumer (`LeaderboardRefreshJob`, `LeaderboardRefreshService` indirectly via the new state table, `LeaderboardRefreshState`, `LeaderboardController`) references this one shared enum.
- **New table `leaderboard_refresh_state`** (Hibernate-managed — no manual DDL, unlike the MVs): entity `org.steam5.domain.LeaderboardRefreshState`, `@Id leaderboardType` (`LeaderboardType`, `@Enumerated(EnumType.STRING)`, column `leaderboard_type`), `refreshedAt` (`OffsetDateTime`, column `refreshed_at`, not null). Repository `org.steam5.repository.LeaderboardRefreshStateRepository extends JpaRepository<LeaderboardRefreshState, LeaderboardType>`. Upsert is just `repository.save(new LeaderboardRefreshState(type, OffsetDateTime.now()))` — Spring Data JPA's `save()` on an entity with a manually-assigned (non-generated) `@Id` calls `entityManager.merge()`, which correctly inserts-or-updates by primary key; no custom `@Modifying` query needed.
- **Response header name (exact):** `X-Leaderboard-Refreshed-At`, ISO-8601 value (`OffsetDateTime#toString()`), added to `/api/leaderboard/monthly`, `/weekly` (only when `floating=true`), `/season`, and `/all` (`""`/`/`/`/all`). **Not** added to `/today` or `/weekly` when `floating=false` (both live, not MV-backed). Header is simply omitted (not set) when no `leaderboard_refresh_state` row exists yet for that type — callers must not assume it's always present.
- **`LeaderboardMvBootstrapConfig`** (new, in `org.steam5.config`, following the exact style of the existing `DataSourcePoolLoggingConfig`: `@Configuration` + `@Bean ApplicationRunner` taking a raw `DataSource`). Reads `db/mv-leaderboard-{all-time,monthly,weekly,season}.sql` as classpath resources, splits each on `;` into exactly 2 statements (`CREATE MATERIALIZED VIEW ... WITH NO DATA` then `CREATE UNIQUE INDEX CONCURRENTLY ...` — this split is safe because none of the 4 files contain a semicolon anywhere except at the end of each of those two statements), checks existence via `pg_matviews.matviewname`/`pg_indexes.indexname`, executes whichever statement is missing over a `Connection` with `setAutoCommit(true)`. Any `SQLException`/`IOException` for one MV is caught, logged at ERROR, and does not stop the other three or fail application startup.
- **Bootstrap config property (exact):** `app.leaderboard-mv.bootstrap.enabled`, default `true` (`${LEADERBOARD_MV_BOOTSTRAP:true}` in `application.yml`; direct `true` literal in `application-dev.yml`, matching that file's convention of no env-var indirection).
- **`jobs.leaderboard-refresh-{all-time,monthly,weekly,season}.enabled` flip to default `true`** in `application.yml` (currently `false`) — `application-dev.yml` already has all four as `true`, no change needed there.
- **Frontend scope:** the "Last updated" paragraph renders only for `LeaderboardTable` modes `all`, `season`, `weekly-floating` — never for `today` or `weekly` (non-floating). This matches the header's own scope (all 4 MV-backed API endpoints get the header, but there's no `/monthly` *page* in the frontend to render it on, and the non-floating live paths never get the header at all).
- **`formatRefreshedAt`** (new, exported from `src/lib/leaderboard.ts`): `(iso: string | null | undefined) => string | null`. Returns `null` for missing/unparseable input so the caller can hide the paragraph entirely rather than show a broken timestamp. Uses `Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' })` (browser default locale/timezone — this is what "localized" means here).
- **`LeaderboardService.LeaderEntry` and `LeaderboardMvRepository`/`LeaderboardRefreshService` public method names are unchanged** by this plan — only `LeaderboardController` (new header) and the frontend fetcher change on the read side.

---

### Task 1: Promote `LeaderboardType` to a shared domain enum

**Files:**
- Create: `backend/src/main/java/org/steam5/domain/LeaderboardType.java`
- Modify: `backend/src/main/java/org/steam5/job/LeaderboardRefreshJob.java`

**Interfaces:**
- Produces: `org.steam5.domain.LeaderboardType` with constants `ALL_TIME, MONTHLY, WEEKLY, SEASON`, consumed by Task 2 (`LeaderboardRefreshState`) and Task 4 (`LeaderboardController`).
- Consumes: nothing new.

No behavior change — this is a pure move. `LeaderboardRefreshJobTest` references the type only via string literals (`"ALL_TIME"` etc.) passed through `JobDataMap`, never the enum type directly, so it needs no changes.

- [ ] **Step 1: Create `LeaderboardType.java`**

```java
package org.steam5.domain;

/**
 * Identifies which leaderboard materialized view a refresh/job/freshness-tracking
 * operation applies to. Shared by LeaderboardRefreshJob (JobDataMap dispatch),
 * LeaderboardRefreshService, and LeaderboardRefreshState.
 */
public enum LeaderboardType {
    ALL_TIME, MONTHLY, WEEKLY, SEASON
}
```

- [ ] **Step 2: Update `LeaderboardRefreshJob.java` to use the shared enum**

Find:

```java
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
```

Replace with:

```java
import org.steam5.domain.LeaderboardType;
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

    private final LeaderboardRefreshService refreshService;
```

Nothing else in the file changes — every remaining reference to `LeaderboardType` (the `execute()` method, the four `.usingJobData("type", LeaderboardType.X.name())` calls) already refers to it unqualified, and now resolves via the import instead of the nested declaration.

- [ ] **Step 3: Compile check**

```bash
/Users/nerlich/workspace/luca/steam5/backend/gradlew -p /Users/nerlich/workspace/luca/steam5/backend compileJava compileTestJava
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Run the existing job test to confirm no regression**

```bash
/Users/nerlich/workspace/luca/steam5/backend/gradlew -p /Users/nerlich/workspace/luca/steam5/backend test --tests "org.steam5.job.LeaderboardRefreshJobTest"
```

Expected: `BUILD SUCCESSFUL`, all 7 tests passing.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/org/steam5/domain/LeaderboardType.java backend/src/main/java/org/steam5/job/LeaderboardRefreshJob.java
git commit -m "refactor(domain): promote LeaderboardType to a shared enum"
```

---

### Task 2: `LeaderboardRefreshState` freshness tracking

**Files:**
- Create: `backend/src/main/java/org/steam5/domain/LeaderboardRefreshState.java`
- Create: `backend/src/main/java/org/steam5/repository/LeaderboardRefreshStateRepository.java`
- Modify: `backend/src/main/java/org/steam5/service/LeaderboardRefreshService.java`
- Modify: `backend/src/test/java/org/steam5/service/LeaderboardRefreshServiceTest.java`

**Interfaces:**
- Consumes: `LeaderboardType` from Task 1.
- Produces: `LeaderboardRefreshStateRepository` (in particular `findById(LeaderboardType)` returning `Optional<LeaderboardRefreshState>`), consumed by Task 4's controller.

- [ ] **Step 1: Update `LeaderboardRefreshServiceTest.java` to expect the new dependency and state-recording behavior**

Replace the entire file with:

```java
package org.steam5.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.steam5.domain.LeaderboardRefreshState;
import org.steam5.domain.LeaderboardType;
import org.steam5.repository.LeaderboardMvRepository;
import org.steam5.repository.LeaderboardRefreshStateRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LeaderboardRefreshServiceTest {

    private LeaderboardMvRepository leaderboardMvRepository;
    private LeaderboardRefreshStateRepository refreshStateRepository;
    private LeaderboardRefreshService service;

    @BeforeEach
    void setUp() {
        leaderboardMvRepository = mock(LeaderboardMvRepository.class);
        refreshStateRepository = mock(LeaderboardRefreshStateRepository.class);
        service = new LeaderboardRefreshService(leaderboardMvRepository, refreshStateRepository);
    }

    @Test
    void refreshAllTime_whenPopulated_usesConcurrentRefreshAndRecordsState() {
        when(leaderboardMvRepository.isPopulated("mv_leaderboard_all_time")).thenReturn(true);

        service.refreshAllTime();

        verify(leaderboardMvRepository).refreshAllTimeConcurrently();
        verify(leaderboardMvRepository, never()).refreshAllTimeFull();

        ArgumentCaptor<LeaderboardRefreshState> captor = ArgumentCaptor.forClass(LeaderboardRefreshState.class);
        verify(refreshStateRepository).save(captor.capture());
        assertEquals(LeaderboardType.ALL_TIME, captor.getValue().getLeaderboardType());
        assertNotNull(captor.getValue().getRefreshedAt());
    }

    @Test
    void refreshAllTime_whenNotPopulated_fallsBackToFullRefreshAndRecordsState() {
        when(leaderboardMvRepository.isPopulated("mv_leaderboard_all_time")).thenReturn(false);

        service.refreshAllTime();

        verify(leaderboardMvRepository).refreshAllTimeFull();
        verify(leaderboardMvRepository, never()).refreshAllTimeConcurrently();

        ArgumentCaptor<LeaderboardRefreshState> captor = ArgumentCaptor.forClass(LeaderboardRefreshState.class);
        verify(refreshStateRepository).save(captor.capture());
        assertEquals(LeaderboardType.ALL_TIME, captor.getValue().getLeaderboardType());
    }

    @Test
    void refreshSeason_whenIsPopulatedReturnsNull_fallsBackToFullRefreshAndRecordsState() {
        // A null result (e.g. the view row is missing from pg_matviews) must not NPE —
        // treat it the same as "not populated" so the failure surfaces from the REFRESH
        // statement itself (missing relation) rather than a silent skip.
        when(leaderboardMvRepository.isPopulated("mv_leaderboard_season")).thenReturn(null);

        service.refreshSeason();

        verify(leaderboardMvRepository).refreshSeasonFull();
        verify(leaderboardMvRepository, never()).refreshSeasonConcurrently();

        ArgumentCaptor<LeaderboardRefreshState> captor = ArgumentCaptor.forClass(LeaderboardRefreshState.class);
        verify(refreshStateRepository).save(captor.capture());
        assertEquals(LeaderboardType.SEASON, captor.getValue().getLeaderboardType());
    }

    @Test
    void refreshMonthly_whenPopulated_usesConcurrentRefreshAndRecordsState() {
        when(leaderboardMvRepository.isPopulated("mv_leaderboard_monthly")).thenReturn(true);

        service.refreshMonthly();

        verify(leaderboardMvRepository).refreshMonthlyConcurrently();
        verify(leaderboardMvRepository, never()).refreshMonthlyFull();

        ArgumentCaptor<LeaderboardRefreshState> captor = ArgumentCaptor.forClass(LeaderboardRefreshState.class);
        verify(refreshStateRepository).save(captor.capture());
        assertEquals(LeaderboardType.MONTHLY, captor.getValue().getLeaderboardType());
    }

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

- [ ] **Step 2: Run it to confirm it fails to compile**

```bash
/Users/nerlich/workspace/luca/steam5/backend/gradlew -p /Users/nerlich/workspace/luca/steam5/backend test --tests "org.steam5.service.LeaderboardRefreshServiceTest"
```

Expected: compile error — `LeaderboardRefreshState`, `LeaderboardRefreshStateRepository`, and the 2-arg `LeaderboardRefreshService` constructor don't exist yet.

- [ ] **Step 3: Create `LeaderboardRefreshState.java`**

```java
package org.steam5.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Column;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * Tracks when each leaderboard materialized view was last successfully refreshed.
 * An ordinary Hibernate-managed table (unlike the MVs themselves, which are manual/
 * bootstrapped DDL) — ddl-auto creates and maintains this one. Written by
 * LeaderboardRefreshService after each successful refresh; read by
 * LeaderboardController to set the X-Leaderboard-Refreshed-At response header.
 */
@Entity
@Table(name = "leaderboard_refresh_state")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LeaderboardRefreshState {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "leaderboard_type", length = 32)
    private LeaderboardType leaderboardType;

    @Column(name = "refreshed_at", nullable = false)
    private OffsetDateTime refreshedAt;
}
```

- [ ] **Step 4: Create `LeaderboardRefreshStateRepository.java`**

```java
package org.steam5.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.steam5.domain.LeaderboardRefreshState;
import org.steam5.domain.LeaderboardType;

public interface LeaderboardRefreshStateRepository extends JpaRepository<LeaderboardRefreshState, LeaderboardType> {
}
```

- [ ] **Step 5: Update `LeaderboardRefreshService.java`**

Replace the entire file with:

```java
package org.steam5.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.steam5.domain.LeaderboardRefreshState;
import org.steam5.domain.LeaderboardType;
import org.steam5.repository.LeaderboardMvRepository;
import org.steam5.repository.LeaderboardRefreshStateRepository;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class LeaderboardRefreshService {

    private final LeaderboardMvRepository leaderboardMvRepository;
    private final LeaderboardRefreshStateRepository refreshStateRepository;

    public void refreshAllTime() {
        refresh(LeaderboardType.ALL_TIME, "mv_leaderboard_all_time", leaderboardMvRepository::refreshAllTimeConcurrently, leaderboardMvRepository::refreshAllTimeFull);
    }

    public void refreshMonthly() {
        refresh(LeaderboardType.MONTHLY, "mv_leaderboard_monthly", leaderboardMvRepository::refreshMonthlyConcurrently, leaderboardMvRepository::refreshMonthlyFull);
    }

    public void refreshWeekly() {
        refresh(LeaderboardType.WEEKLY, "mv_leaderboard_weekly", leaderboardMvRepository::refreshWeeklyConcurrently, leaderboardMvRepository::refreshWeeklyFull);
    }

    public void refreshSeason() {
        refresh(LeaderboardType.SEASON, "mv_leaderboard_season", leaderboardMvRepository::refreshSeasonConcurrently, leaderboardMvRepository::refreshSeasonFull);
    }

    /**
     * REFRESH MATERIALIZED VIEW CONCURRENTLY requires the view to already be populated
     * (see mv-leaderboard-*.sql, created WITH NO DATA). Before that first population, fall
     * back to a plain REFRESH so the job self-heals instead of failing forever. On success,
     * records the refresh timestamp so LeaderboardController can report data freshness.
     */
    private void refresh(final LeaderboardType type, final String viewName, final Runnable concurrentRefresh, final Runnable fullRefresh) {
        final boolean populated = Boolean.TRUE.equals(leaderboardMvRepository.isPopulated(viewName));
        if (populated) {
            concurrentRefresh.run();
        } else {
            log.info("{} not yet populated — running non-concurrent initial REFRESH", viewName);
            fullRefresh.run();
        }
        refreshStateRepository.save(new LeaderboardRefreshState(type, OffsetDateTime.now()));
    }
}
```

- [ ] **Step 6: Run the test to confirm it passes**

```bash
/Users/nerlich/workspace/luca/steam5/backend/gradlew -p /Users/nerlich/workspace/luca/steam5/backend test --tests "org.steam5.service.LeaderboardRefreshServiceTest"
```

Expected: `BUILD SUCCESSFUL`, all 5 tests passing.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/org/steam5/domain/LeaderboardRefreshState.java backend/src/main/java/org/steam5/repository/LeaderboardRefreshStateRepository.java backend/src/main/java/org/steam5/service/LeaderboardRefreshService.java backend/src/test/java/org/steam5/service/LeaderboardRefreshServiceTest.java
git commit -m "feat(service): record leaderboard_refresh_state after each successful MV refresh"
```

---

### Task 3: Auto-bootstrap the leaderboard MVs at startup

**Files:**
- Create: `backend/src/main/java/org/steam5/config/LeaderboardMvBootstrapConfig.java`
- Create: `backend/src/test/java/org/steam5/config/LeaderboardMvBootstrapConfigTest.java`
- Modify: `backend/src/main/resources/application.yml`
- Modify: `backend/src/main/resources/application-dev.yml`

**Interfaces:**
- Consumes: the existing `db/mv-leaderboard-*.sql` classpath resources (Task 1 of the original leaderboard-MV plan; unchanged by this task).
- Produces: nothing consumed by later tasks in this plan — this task is self-contained.

- [ ] **Step 1: Write `LeaderboardMvBootstrapConfigTest.java`**

```java
package org.steam5.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LeaderboardMvBootstrapConfigTest {

    private DataSource dataSource;
    private Connection connection;
    private Statement statement;
    private LeaderboardMvBootstrapConfig config;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = mock(DataSource.class);
        connection = mock(Connection.class);
        statement = mock(Statement.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        config = new LeaderboardMvBootstrapConfig();
    }

    /** Stubs the pg_matviews / pg_indexes existence checks for every one of the 4 MVs. */
    private void stubExistence(boolean viewExists, boolean indexExists) throws Exception {
        PreparedStatement viewCheck = mock(PreparedStatement.class);
        ResultSet viewRs = mock(ResultSet.class);
        when(viewRs.next()).thenReturn(viewExists);
        when(viewCheck.executeQuery()).thenReturn(viewRs);

        PreparedStatement indexCheck = mock(PreparedStatement.class);
        ResultSet indexRs = mock(ResultSet.class);
        when(indexRs.next()).thenReturn(indexExists);
        when(indexCheck.executeQuery()).thenReturn(indexRs);

        when(connection.prepareStatement(argThat(sql -> sql != null && sql.contains("pg_matviews")))).thenReturn(viewCheck);
        when(connection.prepareStatement(argThat(sql -> sql != null && sql.contains("pg_indexes")))).thenReturn(indexCheck);
    }

    @Test
    void bootstrap_neitherExists_createsViewAndIndexForEachOfTheFourMvs() throws Exception {
        stubExistence(false, false);

        config.bootstrapLeaderboardMvs(dataSource).run(mock(ApplicationArguments.class));

        // 4 MVs x 2 statements (CREATE MATERIALIZED VIEW + CREATE UNIQUE INDEX CONCURRENTLY) each
        verify(statement, times(8)).execute(any(String.class));
        verify(connection, times(4)).setAutoCommit(true);
    }

    @Test
    void bootstrap_bothAlreadyExist_createsNothing() throws Exception {
        stubExistence(true, true);

        config.bootstrapLeaderboardMvs(dataSource).run(mock(ApplicationArguments.class));

        verify(statement, never()).execute(any(String.class));
    }

    @Test
    void bootstrap_viewExistsButIndexMissing_createsOnlyTheIndex() throws Exception {
        stubExistence(true, false);

        config.bootstrapLeaderboardMvs(dataSource).run(mock(ApplicationArguments.class));

        // 4 MVs x 1 statement (CREATE UNIQUE INDEX CONCURRENTLY only)
        verify(statement, times(4)).execute(any(String.class));
    }

    @Test
    void bootstrap_getConnectionThrows_doesNotPropagate() throws Exception {
        when(dataSource.getConnection()).thenThrow(new SQLException("connection refused"));

        // Must not throw — the ApplicationRunner logs and continues past a broken MV rather
        // than failing application startup.
        config.bootstrapLeaderboardMvs(dataSource).run(mock(ApplicationArguments.class));
    }
}
```

Note: this test exercises the *real* `db/mv-leaderboard-*.sql` files from the main resources classpath (test classpath includes them automatically) — it's a regression check that those files still split into exactly 2 statements each, not just a test of made-up fixtures.

- [ ] **Step 2: Run it to confirm it fails to compile**

```bash
/Users/nerlich/workspace/luca/steam5/backend/gradlew -p /Users/nerlich/workspace/luca/steam5/backend test --tests "org.steam5.config.LeaderboardMvBootstrapConfigTest"
```

Expected: compile error — `LeaderboardMvBootstrapConfig` doesn't exist yet.

- [ ] **Step 3: Create `LeaderboardMvBootstrapConfig.java`**

```java
package org.steam5.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;

/**
 * Creates the four leaderboard materialized views and their unique indexes at startup if
 * they don't already exist, reading the canonical DDL from db/mv-leaderboard-*.sql — the
 * same files an operator would otherwise apply manually via psql. Uses a raw JDBC
 * connection with autocommit, not Hibernate's ddl-auto, because CREATE INDEX CONCURRENTLY
 * cannot run inside a transaction block.
 *
 * <p>Gated by {@code app.leaderboard-mv.bootstrap.enabled} (default true) so ops retain an
 * escape hatch — e.g. a DBA who wants to control CREATE INDEX CONCURRENTLY timing on a huge
 * production table themselves.</p>
 */
@Configuration
@Slf4j
public class LeaderboardMvBootstrapConfig {

    private record MvDefinition(String viewName, String indexName, String resourcePath) {
    }

    private static final List<MvDefinition> MV_DEFINITIONS = List.of(
            new MvDefinition("mv_leaderboard_all_time", "ux_mv_leaderboard_all_time_steam_id", "db/mv-leaderboard-all-time.sql"),
            new MvDefinition("mv_leaderboard_monthly", "ux_mv_leaderboard_monthly_steam_id", "db/mv-leaderboard-monthly.sql"),
            new MvDefinition("mv_leaderboard_weekly", "ux_mv_leaderboard_weekly_steam_id", "db/mv-leaderboard-weekly.sql"),
            new MvDefinition("mv_leaderboard_season", "ux_mv_leaderboard_season_steam_id", "db/mv-leaderboard-season.sql")
    );

    @Bean
    @ConditionalOnProperty(prefix = "app.leaderboard-mv.bootstrap", name = "enabled", havingValue = "true", matchIfMissing = true)
    public ApplicationRunner bootstrapLeaderboardMvs(final DataSource dataSource) {
        return args -> {
            for (final MvDefinition mv : MV_DEFINITIONS) {
                try {
                    bootstrapOne(dataSource, mv);
                } catch (Exception e) {
                    log.error("Failed to bootstrap materialized view {}", mv.viewName(), e);
                }
            }
        };
    }

    private void bootstrapOne(final DataSource dataSource, final MvDefinition mv) throws SQLException, IOException {
        final List<String> statements = readStatements(mv.resourcePath());
        if (statements.size() != 2) {
            log.error("Expected exactly 2 statements in {}, found {} — skipping", mv.resourcePath(), statements.size());
            return;
        }
        final String createViewSql = statements.get(0);
        final String createIndexSql = statements.get(1);

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(true);

            if (viewExists(connection, mv.viewName())) {
                log.info("Materialized view {} already exists — skipping creation", mv.viewName());
            } else {
                try (Statement statement = connection.createStatement()) {
                    statement.execute(createViewSql);
                }
                log.info("Created materialized view {} (WITH NO DATA)", mv.viewName());
            }

            if (indexExists(connection, mv.indexName())) {
                log.info("Index {} already exists — skipping creation", mv.indexName());
            } else {
                try (Statement statement = connection.createStatement()) {
                    statement.execute(createIndexSql);
                }
                log.info("Created unique index {}", mv.indexName());
            }
        }
    }

    private boolean viewExists(final Connection connection, final String viewName) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT 1 FROM pg_matviews WHERE matviewname = ?")) {
            ps.setString(1, viewName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private boolean indexExists(final Connection connection, final String indexName) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT 1 FROM pg_indexes WHERE indexname = ?")) {
            ps.setString(1, indexName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private List<String> readStatements(final String resourcePath) throws IOException {
        final byte[] bytes;
        try (var inputStream = new ClassPathResource(resourcePath).getInputStream()) {
            bytes = inputStream.readAllBytes();
        }
        final String content = new String(bytes, StandardCharsets.UTF_8);
        return Arrays.stream(content.split(";"))
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
```

- [ ] **Step 4: Run the test to confirm it passes**

```bash
/Users/nerlich/workspace/luca/steam5/backend/gradlew -p /Users/nerlich/workspace/luca/steam5/backend test --tests "org.steam5.config.LeaderboardMvBootstrapConfigTest"
```

Expected: `BUILD SUCCESSFUL`, all 4 tests passing.

- [ ] **Step 5: Add the bootstrap flag and flip refresh-job defaults in `application.yml`**

Find:

```yaml
  leaderboard-refresh-all-time:
    enabled: ${JOB_LEADERBOARD_REFRESH_ALL_TIME:false}
  leaderboard-refresh-monthly:
    enabled: ${JOB_LEADERBOARD_REFRESH_MONTHLY:false}
  leaderboard-refresh-weekly:
    enabled: ${JOB_LEADERBOARD_REFRESH_WEEKLY:false}
  leaderboard-refresh-season:
    enabled: ${JOB_LEADERBOARD_REFRESH_SEASON:false}
```

Replace with:

```yaml
  leaderboard-refresh-all-time:
    enabled: ${JOB_LEADERBOARD_REFRESH_ALL_TIME:true}
  leaderboard-refresh-monthly:
    enabled: ${JOB_LEADERBOARD_REFRESH_MONTHLY:true}
  leaderboard-refresh-weekly:
    enabled: ${JOB_LEADERBOARD_REFRESH_WEEKLY:true}
  leaderboard-refresh-season:
    enabled: ${JOB_LEADERBOARD_REFRESH_SEASON:true}

app:
  leaderboard-mv:
    bootstrap:
      enabled: ${LEADERBOARD_MV_BOOTSTRAP:true}
```

(This is the end of the file — the new `app:` block goes after the last `jobs:` entry, at the same top level as `jobs:`/`auth:`/`admin:`/etc.)

- [ ] **Step 6: Add the bootstrap flag to `application-dev.yml`**

Find:

```yaml
  leaderboard-refresh-season:
    enabled: true
```

Replace with:

```yaml
  leaderboard-refresh-season:
    enabled: true

app:
  leaderboard-mv:
    bootstrap:
      enabled: true
```

- [ ] **Step 7: Run the full suite**

```bash
/Users/nerlich/workspace/luca/steam5/backend/gradlew -p /Users/nerlich/workspace/luca/steam5/backend test
```

Expected: `BUILD SUCCESSFUL`, only the pre-existing `Steam5ApplicationTests#contextLoads` failure.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/org/steam5/config/LeaderboardMvBootstrapConfig.java backend/src/test/java/org/steam5/config/LeaderboardMvBootstrapConfigTest.java backend/src/main/resources/application.yml backend/src/main/resources/application-dev.yml
git commit -m "feat(config): auto-bootstrap leaderboard MVs/indexes at startup; enable refresh jobs by default"
```

---

### Task 4: `LeaderboardController` — `X-Leaderboard-Refreshed-At` header

**Files:**
- Modify: `backend/src/main/java/org/steam5/web/LeaderboardController.java`
- Modify: `backend/src/test/java/org/steam5/web/LeaderboardControllerTest.java`

**Interfaces:**
- Consumes: `LeaderboardRefreshStateRepository` from Task 2.
- Produces: the `X-Leaderboard-Refreshed-At` header on `/monthly`, `/weekly?floating=true`, `/season`, `/all`, consumed by Task 6's frontend fetcher.
- `LeaderboardController` constructor becomes 6-arg: `(GuessRepository, ReviewGameStateService, SeasonService, CacheManager, LeaderboardService, LeaderboardRefreshStateRepository)` — the new dependency is appended last, matching the field-declaration-order convention `@RequiredArgsConstructor` relies on.

- [ ] **Step 1: Replace `LeaderboardControllerTest.java` with the full new version**

```java
package org.steam5.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.ResponseEntity;
import org.steam5.domain.Guess;
import org.steam5.domain.LeaderboardRefreshState;
import org.steam5.domain.LeaderboardType;
import org.steam5.domain.ReviewGamePick;
import org.steam5.domain.Season;
import org.steam5.repository.GuessRepository;
import org.steam5.repository.LeaderboardRefreshStateRepository;
import org.steam5.service.LeaderboardService;
import org.steam5.service.ReviewGameStateService;
import org.steam5.service.SeasonService;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    private LeaderboardRefreshStateRepository refreshStateRepository;

    @BeforeEach
    void setUp() {
        guessRepository = mock(GuessRepository.class);
        reviewGameStateService = mock(ReviewGameStateService.class);
        seasonService = mock(SeasonService.class);
        cacheManager = mock(CacheManager.class);
        leaderboardService = mock(LeaderboardService.class);
        refreshStateRepository = mock(LeaderboardRefreshStateRepository.class);
        when(refreshStateRepository.findById(any(LeaderboardType.class))).thenReturn(Optional.empty());
    }

    private LeaderboardController newController() {
        return new LeaderboardController(guessRepository, reviewGameStateService, seasonService, cacheManager, leaderboardService, refreshStateRepository);
    }

    @Test
    void today_fetchesGuessesThenDelegatesToService() {
        LeaderboardController c = newController();
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
        assertNull(res.getHeaders().getFirst("X-Leaderboard-Refreshed-At"));
    }

    @Test
    void allTime_delegatesToServiceAndSetsRefreshedAtHeaderWhenStateExists() {
        LeaderboardController c = newController();

        List<LeaderboardService.LeaderEntry> canned = List.of(
                new LeaderboardService.LeaderEntry("u1", "User One", 5L, 1L, 1L, 0L, 0L, 0L, 5.0, 1, null, null, null),
                new LeaderboardService.LeaderEntry("u2", "u2", 1L, 1L, 0L, 0L, 1L, 0L, 1.0, 0, null, null, null)
        );
        when(leaderboardService.buildAllTimeLeaderboard(any(LocalDate.class))).thenReturn(canned);

        OffsetDateTime refreshedAt = OffsetDateTime.of(2026, 7, 24, 0, 40, 0, 0, ZoneOffset.UTC);
        when(refreshStateRepository.findById(LeaderboardType.ALL_TIME))
                .thenReturn(Optional.of(new LeaderboardRefreshState(LeaderboardType.ALL_TIME, refreshedAt)));

        ResponseEntity<List<LeaderboardService.LeaderEntry>> res = c.allTime();
        assertEquals(200, res.getStatusCode().value());
        assertSame(canned, res.getBody());
        verify(leaderboardService).buildAllTimeLeaderboard(any(LocalDate.class));
        verifyNoInteractions(guessRepository);
        assertEquals(refreshedAt.toString(), res.getHeaders().getFirst("X-Leaderboard-Refreshed-At"));
    }

    @Test
    void allTime_noRefreshStateYet_omitsHeader() {
        LeaderboardController c = newController();
        when(leaderboardService.buildAllTimeLeaderboard(any(LocalDate.class))).thenReturn(List.of());

        ResponseEntity<List<LeaderboardService.LeaderEntry>> res = c.allTime();

        assertNull(res.getHeaders().getFirst("X-Leaderboard-Refreshed-At"));
    }

    @Test
    void weekly_floating_delegatesToMvBackedServiceAndSetsHeader() {
        LeaderboardController c = newController();
        when(reviewGameStateService.generateDailyPicks()).thenReturn(List.of());

        List<LeaderboardService.LeaderEntry> canned = List.of(
                new LeaderboardService.LeaderEntry("u1", "User One", 7L, 2L, 1L, 0L, 0L, 1L, 3.5, 1, null, null, null)
        );
        when(leaderboardService.buildWeeklyLeaderboard(any(LocalDate.class))).thenReturn(canned);

        OffsetDateTime refreshedAt = OffsetDateTime.of(2026, 7, 24, 0, 44, 0, 0, ZoneOffset.UTC);
        when(refreshStateRepository.findById(LeaderboardType.WEEKLY))
                .thenReturn(Optional.of(new LeaderboardRefreshState(LeaderboardType.WEEKLY, refreshedAt)));

        ResponseEntity<List<LeaderboardService.LeaderEntry>> res = c.weekly(true);

        assertEquals(200, res.getStatusCode().value());
        assertSame(canned, res.getBody());
        verify(leaderboardService).buildWeeklyLeaderboard(any(LocalDate.class));
        verifyNoInteractions(guessRepository);
        assertEquals(refreshedAt.toString(), res.getHeaders().getFirst("X-Leaderboard-Refreshed-At"));
    }

    @Test
    void weekly_nonFloating_usesLiveQueryAndOmitsHeader() {
        LeaderboardController c = newController();
        when(reviewGameStateService.generateDailyPicks()).thenReturn(List.of());
        when(guessRepository.findAllBetween(any(LocalDate.class), any(LocalDate.class))).thenReturn(List.of());
        when(leaderboardService.buildLeaderboard(any(), any())).thenReturn(List.of());

        ResponseEntity<List<LeaderboardService.LeaderEntry>> res = c.weekly(false);

        assertEquals(200, res.getStatusCode().value());
        verify(guessRepository).findAllBetween(any(LocalDate.class), any(LocalDate.class));
        verify(leaderboardService).buildLeaderboard(any(), any());
        verify(leaderboardService, never()).buildWeeklyLeaderboard(any());
        verifyNoInteractions(refreshStateRepository);
        assertNull(res.getHeaders().getFirst("X-Leaderboard-Refreshed-At"));
    }

    @Test
    void monthly_delegatesToMvBackedServiceAndSetsHeader() {
        LeaderboardController c = newController();
        when(reviewGameStateService.generateDailyPicks()).thenReturn(List.of());

        List<LeaderboardService.LeaderEntry> canned = List.of(
                new LeaderboardService.LeaderEntry("u1", "User One", 10L, 2L, 1L, 0L, 0L, 1L, 5.0, 1, null, null, null)
        );
        when(leaderboardService.buildMonthlyLeaderboard(any(LocalDate.class))).thenReturn(canned);

        OffsetDateTime refreshedAt = OffsetDateTime.of(2026, 7, 24, 0, 42, 0, 0, ZoneOffset.UTC);
        when(refreshStateRepository.findById(LeaderboardType.MONTHLY))
                .thenReturn(Optional.of(new LeaderboardRefreshState(LeaderboardType.MONTHLY, refreshedAt)));

        ResponseEntity<List<LeaderboardService.LeaderEntry>> res = c.monthly();

        assertEquals(200, res.getStatusCode().value());
        assertSame(canned, res.getBody());
        verify(leaderboardService).buildMonthlyLeaderboard(any(LocalDate.class));
        verifyNoInteractions(guessRepository);
        assertEquals(refreshedAt.toString(), res.getHeaders().getFirst("X-Leaderboard-Refreshed-At"));
    }

    @Test
    void season_cacheMiss_delegatesToMvBackedServiceCachesResultAndSetsHeader() {
        LeaderboardController c = newController();
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

        OffsetDateTime refreshedAt = OffsetDateTime.of(2026, 7, 24, 0, 46, 0, 0, ZoneOffset.UTC);
        when(refreshStateRepository.findById(LeaderboardType.SEASON))
                .thenReturn(Optional.of(new LeaderboardRefreshState(LeaderboardType.SEASON, refreshedAt)));

        ResponseEntity<List<LeaderboardService.LeaderEntry>> res = c.season();

        assertEquals(200, res.getStatusCode().value());
        assertSame(canned, res.getBody());
        verify(leaderboardService).buildSeasonLeaderboard(any(LocalDate.class));
        verify(cache).put(anyString(), any());
        assertEquals(refreshedAt.toString(), res.getHeaders().getFirst("X-Leaderboard-Refreshed-At"));
    }

    @Test
    void season_cacheHit_skipsMvBackedServiceButStillSetsHeader() {
        LeaderboardController c = newController();
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

        OffsetDateTime refreshedAt = OffsetDateTime.of(2026, 7, 24, 0, 46, 0, 0, ZoneOffset.UTC);
        when(refreshStateRepository.findById(LeaderboardType.SEASON))
                .thenReturn(Optional.of(new LeaderboardRefreshState(LeaderboardType.SEASON, refreshedAt)));

        ResponseEntity<List<LeaderboardService.LeaderEntry>> res = c.season();

        assertEquals(200, res.getStatusCode().value());
        assertSame(cached, res.getBody());
        verifyNoInteractions(leaderboardService);
        assertEquals(refreshedAt.toString(), res.getHeaders().getFirst("X-Leaderboard-Refreshed-At"));
    }
}
```

- [ ] **Step 2: Run it to confirm it fails to compile / fails**

```bash
/Users/nerlich/workspace/luca/steam5/backend/gradlew -p /Users/nerlich/workspace/luca/steam5/backend test --tests "org.steam5.web.LeaderboardControllerTest"
```

Expected: compile error — the 6-arg constructor and `LeaderboardRefreshStateRepository` don't exist yet.

- [ ] **Step 3: Update `LeaderboardController.java`**

Find:

```java
import org.steam5.domain.GameDate;
import org.steam5.domain.Guess;
import org.steam5.domain.ReviewGamePick;
import org.steam5.domain.Season;
import org.steam5.repository.GuessRepository;
import org.steam5.service.LeaderboardService;
import org.steam5.service.ReviewGameStateService;
import org.steam5.service.SeasonService;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/leaderboard")
@Validated
public class LeaderboardController {

    private final GuessRepository guessRepository;
    private final ReviewGameStateService reviewGameStateService;
    private final SeasonService seasonService;
    private final CacheManager cacheManager;
    private final LeaderboardService leaderboardService;
```

Replace with:

```java
import org.steam5.domain.GameDate;
import org.steam5.domain.Guess;
import org.steam5.domain.LeaderboardType;
import org.steam5.domain.ReviewGamePick;
import org.steam5.domain.Season;
import org.steam5.repository.GuessRepository;
import org.steam5.repository.LeaderboardRefreshStateRepository;
import org.steam5.service.LeaderboardService;
import org.steam5.service.ReviewGameStateService;
import org.steam5.service.SeasonService;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/leaderboard")
@Validated
public class LeaderboardController {

    private final GuessRepository guessRepository;
    private final ReviewGameStateService reviewGameStateService;
    private final SeasonService seasonService;
    private final CacheManager cacheManager;
    private final LeaderboardService leaderboardService;
    private final LeaderboardRefreshStateRepository refreshStateRepository;
```

Find:

```java
        if (floating) {
            return ResponseEntity.ok(leaderboardService.buildWeeklyLeaderboard(today));
        }
```

Replace with:

```java
        if (floating) {
            return withRefreshedAtHeader(LeaderboardType.WEEKLY, leaderboardService.buildWeeklyLeaderboard(today));
        }
```

Find:

```java
    public ResponseEntity<List<LeaderboardService.LeaderEntry>> monthly() {
        final List<ReviewGamePick> picks = reviewGameStateService.generateDailyPicks();
        final LocalDate today = picks.isEmpty() ? GameDate.todayUtc() : picks.getFirst().getPickDate();
        return ResponseEntity.ok(leaderboardService.buildMonthlyLeaderboard(today));
    }
```

Replace with:

```java
    public ResponseEntity<List<LeaderboardService.LeaderEntry>> monthly() {
        final List<ReviewGamePick> picks = reviewGameStateService.generateDailyPicks();
        final LocalDate today = picks.isEmpty() ? GameDate.todayUtc() : picks.getFirst().getPickDate();
        return withRefreshedAtHeader(LeaderboardType.MONTHLY, leaderboardService.buildMonthlyLeaderboard(today));
    }
```

Find:

```java
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

Replace with:

```java
            if (wrapper != null && wrapper.get() instanceof List<?> cached) {
                @SuppressWarnings("unchecked")
                final List<LeaderboardService.LeaderEntry> cachedEntries = (List<LeaderboardService.LeaderEntry>) cached;
                return withRefreshedAtHeader(LeaderboardType.SEASON, cachedEntries);
            }
        }

        final List<LeaderboardService.LeaderEntry> entries = leaderboardService.buildSeasonLeaderboard(asOfDate);
        if (cache != null) {
            cache.put(cacheKey, entries);
        }
        return withRefreshedAtHeader(LeaderboardType.SEASON, entries);
    }
```

Find:

```java
    public ResponseEntity<List<LeaderboardService.LeaderEntry>> allTime() {
        final LocalDate today = GameDate.todayUtc();
        return ResponseEntity.ok(leaderboardService.buildAllTimeLeaderboard(today));
    }
}
```

Replace with:

```java
    public ResponseEntity<List<LeaderboardService.LeaderEntry>> allTime() {
        final LocalDate today = GameDate.todayUtc();
        return withRefreshedAtHeader(LeaderboardType.ALL_TIME, leaderboardService.buildAllTimeLeaderboard(today));
    }

    /**
     * Wraps an MV-backed leaderboard response with the X-Leaderboard-Refreshed-At header,
     * sourced from leaderboard_refresh_state. Omitted (not just empty) when no refresh has
     * happened yet for this type, so callers don't have to distinguish "just refreshed" from
     * "never refreshed" via an empty string.
     */
    private ResponseEntity<List<LeaderboardService.LeaderEntry>> withRefreshedAtHeader(
            final LeaderboardType type, final List<LeaderboardService.LeaderEntry> entries) {
        final ResponseEntity.BodyBuilder builder = ResponseEntity.ok();
        refreshStateRepository.findById(type)
                .ifPresent(state -> builder.header("X-Leaderboard-Refreshed-At", state.getRefreshedAt().toString()));
        return builder.body(entries);
    }
}
```

- [ ] **Step 4: Run the test to confirm it passes**

```bash
/Users/nerlich/workspace/luca/steam5/backend/gradlew -p /Users/nerlich/workspace/luca/steam5/backend test --tests "org.steam5.web.LeaderboardControllerTest"
```

Expected: `BUILD SUCCESSFUL`, all 9 tests passing.

- [ ] **Step 5: Run the full backend suite**

```bash
/Users/nerlich/workspace/luca/steam5/backend/gradlew -p /Users/nerlich/workspace/luca/steam5/backend test
```

Expected: `BUILD SUCCESSFUL`, only the pre-existing `Steam5ApplicationTests#contextLoads` failure.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/org/steam5/web/LeaderboardController.java backend/src/test/java/org/steam5/web/LeaderboardControllerTest.java
git commit -m "feat(web): add X-Leaderboard-Refreshed-At header to MV-backed leaderboard endpoints"
```

---

### Task 5: Frontend `formatRefreshedAt` helper

**Files:**
- Modify: `frontend/src/lib/leaderboard.ts`
- Create: `frontend/src/lib/leaderboard.test.ts`

**Interfaces:**
- Produces: `formatRefreshedAt(iso: string | null | undefined): string | null`, consumed by Task 6's `LeaderboardTable`.
- Consumes: nothing new (pure function, no fetch involved).

- [ ] **Step 1: Write `leaderboard.test.ts`**

```typescript
import {describe, expect, it} from 'vitest';
import {formatRefreshedAt} from './leaderboard';

describe('formatRefreshedAt', () => {
    it('formats a valid ISO timestamp into a non-empty localized string', () => {
        const result = formatRefreshedAt('2026-07-24T00:40:00Z');
        expect(result).not.toBeNull();
        expect(typeof result).toBe('string');
        expect(result!.length).toBeGreaterThan(0);
    });

    it('returns null for null input', () => {
        expect(formatRefreshedAt(null)).toBeNull();
    });

    it('returns null for undefined input', () => {
        expect(formatRefreshedAt(undefined)).toBeNull();
    });

    it('returns null for an empty string', () => {
        expect(formatRefreshedAt('')).toBeNull();
    });

    it('returns null for an unparseable string', () => {
        expect(formatRefreshedAt('not-a-date')).toBeNull();
    });
});
```

- [ ] **Step 2: Run it to confirm it fails**

```bash
npm --prefix /Users/nerlich/workspace/luca/steam5/frontend test -- leaderboard.test.ts
```

Expected: failure — `formatRefreshedAt` is not exported from `./leaderboard` yet.

- [ ] **Step 3: Add `formatRefreshedAt` to `leaderboard.ts`**

Find the end of the file:

```typescript
export async function fetchLeaderboardPageData(
  endpoint: string,
  timeframe: AchievementTimeframe,
  revalidate: number
): Promise<[unknown | null, AchievementsResult]> {
  return Promise.all([
    fetchLeaderboardData(endpoint, revalidate),
    fetchAchievements(timeframe, revalidate),
  ]);
}
```

Append immediately after it:

```typescript

/**
 * Formats an ISO-8601 timestamp (e.g. from the X-Leaderboard-Refreshed-At response
 * header) as a localized date+time string using the browser's own locale/timezone.
 * Returns null for missing or unparseable input, so callers can hide the "last
 * updated" paragraph entirely rather than show a broken timestamp.
 */
export function formatRefreshedAt(iso: string | null | undefined): string | null {
  if (!iso) return null;
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return null;
  return new Intl.DateTimeFormat(undefined, {dateStyle: 'medium', timeStyle: 'short'}).format(date);
}
```

- [ ] **Step 4: Run the test to confirm it passes**

```bash
npm --prefix /Users/nerlich/workspace/luca/steam5/frontend test -- leaderboard.test.ts
```

Expected: all 5 tests passing.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/lib/leaderboard.ts frontend/src/lib/leaderboard.test.ts
git commit -m "feat(frontend): add formatRefreshedAt helper for leaderboard freshness display"
```

---

### Task 6: `LeaderboardTable` — capture and render the freshness header

**Files:**
- Modify: `frontend/src/components/LeaderboardTable.tsx`

**Interfaces:**
- Consumes: `formatRefreshedAt` from Task 5; the `X-Leaderboard-Refreshed-At` response header from Task 4 (already live on the backend by this point in the plan).
- Produces: nothing consumed by later tasks — this is the last functional task.

No existing test file covers `LeaderboardTable.tsx` (no precedent in this codebase for component-level tests here) — this task is manually verified by reading the diff carefully; there is no automated test step.

- [ ] **Step 1: Update the fetcher and the `data`/`refreshedAt` extraction**

Find:

```typescript
const fetcher = (url: string) => fetch(url, {
    headers: {accept: 'application/json'},
    cache: 'no-cache' // Revalidate with server but allow caching for performance
}).then(r => {
    if (!r.ok) throw new Error(`Failed to load ${url}: ${r.status}`);
    return r.json();
});
```

Replace with:

```typescript
type LeaderboardFetchResult = { data: LeaderEntry[]; refreshedAt: string | null };

const fetcher = async (url: string): Promise<LeaderboardFetchResult> => {
    const r = await fetch(url, {
        headers: {accept: 'application/json'},
        cache: 'no-cache' // Revalidate with server but allow caching for performance
    });
    if (!r.ok) throw new Error(`Failed to load ${url}: ${r.status}`);
    const data = await r.json();
    return {data, refreshedAt: r.headers.get('X-Leaderboard-Refreshed-At')};
};
```

Find:

```typescript
    const {data, error, isLoading} = useSWR<LeaderEntry[]>(endpoint, fetcher, {
        refreshInterval,
        revalidateOnFocus: true,
        focusThrottleInterval: refreshInterval,
        fallbackData: props.initialData || undefined,
    });
```

Replace with:

```typescript
    const {data: leaderboardResult, error, isLoading} = useSWR<LeaderboardFetchResult>(endpoint, fetcher, {
        refreshInterval,
        revalidateOnFocus: true,
        focusThrottleInterval: refreshInterval,
        fallbackData: props.initialData ? {data: props.initialData, refreshedAt: null} : undefined,
    });

    const data = leaderboardResult?.data;
    const refreshedAt = leaderboardResult?.refreshedAt ?? null;
```

Everything below this point that already references `data` (the `sorted`/`avgTotalPoints` `useMemo`s, the `error`/`isLoading`/`!data` early returns, the table rendering) needs no changes — `data` still has the same `LeaderEntry[] | undefined` type it always had, just now derived from `leaderboardResult` instead of being the direct SWR value.

- [ ] **Step 2: Add the import for `formatRefreshedAt`**

Find:

```typescript
import AchievementsTable from "@/components/AchievementsTable";
import SortableTH from "@/components/SortableTH";
```

Replace with:

```typescript
import AchievementsTable from "@/components/AchievementsTable";
import SortableTH from "@/components/SortableTH";
import {formatRefreshedAt} from "@/lib/leaderboard";
```

- [ ] **Step 3: Compute whether to show the paragraph, and render it**

Find:

```typescript
    const avgTotalPoints = useMemo(() => {
        const arr = Array.isArray(data) ? data : [];
        if (arr.length === 0) return 0;
        const sum = arr.reduce((acc, e) => acc + (typeof e.totalPoints === 'number' ? e.totalPoints : 0), 0);
        return sum / arr.length;
    }, [data]);
```

Replace with:

```typescript
    const avgTotalPoints = useMemo(() => {
        const arr = Array.isArray(data) ? data : [];
        if (arr.length === 0) return 0;
        const sum = arr.reduce((acc, e) => acc + (typeof e.totalPoints === 'number' ? e.totalPoints : 0), 0);
        return sum / arr.length;
    }, [data]);

    // Only the MV-backed leaderboards have a meaningful refresh cadence to report;
    // 'today' and non-floating 'weekly' are always computed live.
    const showLastUpdated = props.mode === 'season' || props.mode === 'all' || props.mode === 'weekly-floating';
    const lastUpdatedText = showLastUpdated ? formatRefreshedAt(refreshedAt) : null;
```

Find:

```tsx
            <div className="leaderboard__subline" aria-live="polite">
                Average points:&nbsp;<strong>{avgTotalPoints.toFixed(2)}</strong>
            </div>
            
            <AchievementsTable
```

Replace with:

```tsx
            <div className="leaderboard__subline" aria-live="polite">
                Average points:&nbsp;<strong>{avgTotalPoints.toFixed(2)}</strong>
            </div>
            {lastUpdatedText && (
                <p className="text-muted leaderboard__last-updated">
                    Last updated: {lastUpdatedText}
                </p>
            )}

            <AchievementsTable
```

- [ ] **Step 4: Run the frontend test suite**

```bash
npm --prefix /Users/nerlich/workspace/luca/steam5/frontend test
```

Expected: all tests passing (this file has no dedicated tests, but the run must not break `seo.test.ts` or `leaderboard.test.ts` from Task 5 — a TypeScript type error in this file would fail the whole `vitest run`/build).

- [ ] **Step 5: Manual verification**

Start the frontend dev server and visually confirm:

```bash
npm --prefix /Users/nerlich/workspace/luca/steam5/frontend run dev
```

- `/review-guesser/leaderboard` (all-time), `/review-guesser/leaderboard/season`, and `/review-guesser/leaderboard/weekly` (floating, the default) show a "Last updated: ..." line below the average-points line once the backend responds with the header (requires a backend with a populated `leaderboard_refresh_state` row — if running against a fresh local DB with no refresh having happened yet, the paragraph correctly stays hidden).
- `/review-guesser/leaderboard/today` shows no such line.

If no local backend is available to verify against, note that explicitly rather than claiming this step passed.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/components/LeaderboardTable.tsx
git commit -m "feat(frontend): show last-updated timestamp on MV-backed leaderboards"
```

---

### Task 7: README documentation update

**Files:**
- Modify: `README.md`

**Interfaces:**
- Consumes: everything from Tasks 1-6.
- Produces: nothing — documentation only.

- [ ] **Step 1: Update the "Manual application required" rollout steps in "Query Performance Notes"**

Find:

```text
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
```

Replace with:

```text
  - **Zero-touch by default**, no manual `psql` step required: `LeaderboardMvBootstrapConfig` (an
    `ApplicationRunner`, gated by `app.leaderboard-mv.bootstrap.enabled`, default `true`) creates any
    missing MV or unique index at application startup, reading the same `db/mv-leaderboard-*.sql` files
    an operator would otherwise apply by hand — via a raw autocommit JDBC connection, not Hibernate's
    `ddl-auto` (`CREATE INDEX CONCURRENTLY` still can't run inside `ddl-auto`'s transaction, which is why
    this is a dedicated bootstrap step rather than a JPA-managed table). `jobs.leaderboard-refresh-*.enabled`
    also default to `true` now, so `LeaderboardRefreshService` populates each view on its first scheduled
    run — self-healing via `pg_matviews.ispopulated` (falls back to a plain, non-concurrent `REFRESH` the
    first time, then uses `CONCURRENTLY`). Set `app.leaderboard-mv.bootstrap.enabled=false` if a DBA wants
    to control `CREATE INDEX CONCURRENTLY` timing manually on a very large production table instead.
  - Each successful refresh also writes a row to `leaderboard_refresh_state` (an ordinary Hibernate-managed
    table, unlike the MVs themselves), which `LeaderboardController` exposes via an
    `X-Leaderboard-Refreshed-At` response header (ISO-8601) on `/monthly`, `/weekly?floating=true`,
    `/season`, and `/all` — omitted until the first refresh completes. The frontend renders this as a
    localized "Last updated" line below the all-time/season/weekly-floating leaderboards.
```

- [ ] **Step 2: Commit**

```bash
git add README.md
git commit -m "docs: document zero-touch MV bootstrap and the leaderboard freshness header"
```

