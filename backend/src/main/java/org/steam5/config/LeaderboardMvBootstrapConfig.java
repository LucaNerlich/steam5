package org.steam5.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.steam5.domain.LeaderboardType;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Creates the four leaderboard materialized views and their unique indexes at startup if
 * they don't already exist, reading the canonical DDL from db/mv-leaderboard-*.sql — the
 * same files an operator would otherwise apply manually via psql. Uses a raw JDBC
 * connection with autocommit, not Hibernate's ddl-auto, because CREATE INDEX CONCURRENTLY
 * cannot run inside a transaction block.
 *
 * <p>Also runs a one-time initial REFRESH (and records it in {@code leaderboard_refresh_state},
 * matching {@code LeaderboardRefreshService}'s bookkeeping) for any view found unpopulated —
 * whether just created here or already present but never refreshed. Without this, a fresh view
 * stays queryable-but-empty until whichever scheduled refresh job fires next; for the season MV
 * specifically (no intraday trigger, only a once-daily cron) that can be up to 24h, during which
 * every request — including a Next.js build-time prefetch — hits "materialized view ... has not
 * been populated".</p>
 *
 * <p>Gated by {@code app.leaderboard-mv.bootstrap.enabled} (default true) so ops retain an
 * escape hatch — e.g. a DBA who wants to control CREATE INDEX CONCURRENTLY timing on a huge
 * production table themselves.</p>
 */
@Configuration
@Slf4j
public class LeaderboardMvBootstrapConfig {

    private record MvDefinition(LeaderboardType type, String viewName, String indexName, String resourcePath) {
    }

    private static final List<MvDefinition> MV_DEFINITIONS = List.of(
            new MvDefinition(LeaderboardType.ALL_TIME, "mv_leaderboard_all_time", "ux_mv_leaderboard_all_time_steam_id", "db/mv-leaderboard-all-time.sql"),
            new MvDefinition(LeaderboardType.MONTHLY, "mv_leaderboard_monthly", "ux_mv_leaderboard_monthly_steam_id", "db/mv-leaderboard-monthly.sql"),
            new MvDefinition(LeaderboardType.WEEKLY, "mv_leaderboard_weekly", "ux_mv_leaderboard_weekly_steam_id", "db/mv-leaderboard-weekly.sql"),
            new MvDefinition(LeaderboardType.SEASON, "mv_leaderboard_season", "ux_mv_leaderboard_season_steam_id", "db/mv-leaderboard-season.sql")
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

            final Optional<Boolean> existingPopulatedState = queryIsPopulated(connection, mv.viewName());
            if (existingPopulatedState.isPresent()) {
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

            final boolean populated = existingPopulatedState.orElse(false);
            if (!populated) {
                log.info("Materialized view {} not yet populated — running initial REFRESH", mv.viewName());
                try (Statement statement = connection.createStatement()) {
                    statement.execute("REFRESH MATERIALIZED VIEW " + mv.viewName());
                }
                recordRefreshState(connection, mv.type());
                log.info("Populated materialized view {}", mv.viewName());
            }
        }
    }

    private Optional<Boolean> queryIsPopulated(final Connection connection, final String viewName) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT ispopulated FROM pg_matviews WHERE matviewname = ?")) {
            ps.setString(1, viewName);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(rs.getBoolean(1));
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

    /**
     * Mirrors LeaderboardRefreshService's post-refresh bookkeeping so the freshness header/UI
     * reflects this initial population immediately, rather than waiting for the first
     * scheduled refresh to also record it. Raw upsert (matches IngestStateRepository's
     * convention) since this class deliberately has no JPA/repository dependency.
     */
    private void recordRefreshState(final Connection connection, final LeaderboardType type) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO leaderboard_refresh_state (leaderboard_type, refreshed_at) VALUES (?, ?) " +
                        "ON CONFLICT (leaderboard_type) DO UPDATE SET refreshed_at = EXCLUDED.refreshed_at")) {
            ps.setString(1, type.name());
            ps.setObject(2, OffsetDateTime.now());
            ps.executeUpdate();
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
