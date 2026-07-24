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
    private PreparedStatement refreshStateUpsert;
    private LeaderboardMvBootstrapConfig config;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = mock(DataSource.class);
        connection = mock(Connection.class);
        statement = mock(Statement.class);
        refreshStateUpsert = mock(PreparedStatement.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        when(connection.prepareStatement(argThat(sql -> sql != null && sql.contains("leaderboard_refresh_state"))))
                .thenReturn(refreshStateUpsert);
        config = new LeaderboardMvBootstrapConfig();
    }

    /** Stubs the pg_matviews (existence + populated) / pg_indexes existence checks for every one of the 4 MVs. */
    private void stubExistence(boolean viewExists, boolean viewPopulated, boolean indexExists) throws Exception {
        PreparedStatement viewCheck = mock(PreparedStatement.class);
        ResultSet viewRs = mock(ResultSet.class);
        when(viewRs.next()).thenReturn(viewExists);
        when(viewRs.getBoolean(1)).thenReturn(viewPopulated);
        when(viewCheck.executeQuery()).thenReturn(viewRs);

        PreparedStatement indexCheck = mock(PreparedStatement.class);
        ResultSet indexRs = mock(ResultSet.class);
        when(indexRs.next()).thenReturn(indexExists);
        when(indexCheck.executeQuery()).thenReturn(indexRs);

        when(connection.prepareStatement(argThat(sql -> sql != null && sql.contains("pg_matviews")))).thenReturn(viewCheck);
        when(connection.prepareStatement(argThat(sql -> sql != null && sql.contains("pg_indexes")))).thenReturn(indexCheck);
    }

    @Test
    void bootstrap_neitherExistsNorPopulated_createsViewIndexAndPopulatesForEachOfTheFiveMvs() throws Exception {
        stubExistence(false, false, false);

        config.bootstrapLeaderboardMvs(dataSource).run(mock(ApplicationArguments.class));

        // 5 MVs x 3 statements (CREATE MATERIALIZED VIEW + CREATE UNIQUE INDEX CONCURRENTLY +
        // the initial REFRESH) each
        verify(statement, times(15)).execute(any(String.class));
        verify(connection, times(5)).setAutoCommit(true);
        // The initial population is recorded immediately so the freshness header/UI reflects
        // it without waiting for the first scheduled refresh job.
        verify(refreshStateUpsert, times(5)).executeUpdate();
    }

    @Test
    void bootstrap_bothExistAndPopulated_createsOrRefreshesNothing() throws Exception {
        stubExistence(true, true, true);

        config.bootstrapLeaderboardMvs(dataSource).run(mock(ApplicationArguments.class));

        verify(statement, never()).execute(any(String.class));
        verify(refreshStateUpsert, never()).executeUpdate();
    }

    @Test
    void bootstrap_viewExistsAndPopulatedButIndexMissing_createsOnlyTheIndex() throws Exception {
        stubExistence(true, true, false);

        config.bootstrapLeaderboardMvs(dataSource).run(mock(ApplicationArguments.class));

        // 5 MVs x 1 statement (CREATE UNIQUE INDEX CONCURRENTLY only) — already populated, so
        // no initial REFRESH is needed.
        verify(statement, times(5)).execute(any(String.class));
        verify(refreshStateUpsert, never()).executeUpdate();
    }

    @Test
    void bootstrap_viewExistsButNotPopulated_populatesAndRecordsStateWithoutRecreatingTheView() throws Exception {
        // Covers the exact production/dev symptom this fix addresses: a view (and its index)
        // already exist — created by an earlier bootstrap run, or manually — but nothing has
        // ever refreshed it, so every read fails with "has not been populated" until whichever
        // scheduled job fires next (up to 24h away for the season/hardest-games MVs specifically).
        stubExistence(true, false, true);

        config.bootstrapLeaderboardMvs(dataSource).run(mock(ApplicationArguments.class));

        // 5 MVs x 1 statement (REFRESH only) — view+index already exist, just needs populating
        verify(statement, times(5)).execute(any(String.class));
        verify(refreshStateUpsert, times(5)).executeUpdate();
    }

    @Test
    void bootstrap_getConnectionThrows_doesNotPropagate() throws Exception {
        when(dataSource.getConnection()).thenThrow(new SQLException("connection refused"));

        // Must not throw — the ApplicationRunner logs and continues past a broken MV rather
        // than failing application startup.
        config.bootstrapLeaderboardMvs(dataSource).run(mock(ApplicationArguments.class));
    }
}
