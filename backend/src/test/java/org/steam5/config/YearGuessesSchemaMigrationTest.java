package org.steam5.config;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class YearGuessesSchemaMigrationTest {

    @Test
    void dropLegacyBucketColumns_dropsColumnsWhenPresent() {
        final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class), any()))
                .thenReturn(true, false);
        final YearGuessesSchemaMigration migration = new YearGuessesSchemaMigration(jdbcTemplate);

        migration.dropLegacyBucketColumns();

        verify(jdbcTemplate).execute("ALTER TABLE year_guesses DROP COLUMN selected_bucket");
        verify(jdbcTemplate, never()).execute("ALTER TABLE year_guesses DROP COLUMN actual_bucket");
    }

    @Test
    void dropLegacyBucketColumns_skipsWhenColumnsAbsent() {
        final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Boolean.class), any()))
                .thenReturn(false, false);
        final YearGuessesSchemaMigration migration = new YearGuessesSchemaMigration(jdbcTemplate);

        migration.dropLegacyBucketColumns();

        verify(jdbcTemplate, never()).execute(anyString());
    }
}
