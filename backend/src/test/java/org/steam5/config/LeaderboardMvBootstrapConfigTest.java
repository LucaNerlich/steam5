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
