package org.steam5.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Removes legacy bucket columns from {@code year_guesses} after the year game
 * moved from bucket scoring to open year guesses. Hibernate {@code ddl-auto: update}
 * adds new columns but does not drop obsolete NOT NULL columns.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class YearGuessesSchemaMigration {

    private final JdbcTemplate jdbcTemplate;

    @EventListener(ApplicationReadyEvent.class)
    public void dropLegacyBucketColumns() {
        try {
            dropColumnIfExists("selected_bucket");
            dropColumnIfExists("actual_bucket");
        } catch (RuntimeException e) {
            log.warn("year_guesses schema migration skipped: {}", e.toString());
        }
    }

    private void dropColumnIfExists(final String columnName) {
        final Boolean exists = jdbcTemplate.queryForObject(
                """
                        SELECT EXISTS (
                            SELECT 1
                            FROM information_schema.columns
                            WHERE table_schema = current_schema()
                              AND table_name = 'year_guesses'
                              AND column_name = ?
                        )
                        """,
                Boolean.class,
                columnName
        );
        if (Boolean.TRUE.equals(exists)) {
            jdbcTemplate.execute("ALTER TABLE year_guesses DROP COLUMN " + columnName);
            log.info("Dropped legacy year_guesses column {}", columnName);
        }
    }
}
