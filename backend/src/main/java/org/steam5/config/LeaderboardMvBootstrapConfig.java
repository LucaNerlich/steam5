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
