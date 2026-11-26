package org.steam5.db;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every statement in V5 is DROP ... IF EXISTS, so a mistyped constraint name would succeed
 * silently and leave the old whitelist in place (the spotlight job then swallows the
 * violation). Pin each dropped name to the baseline that created it.
 */
class EnumConstraintMigrationTest {

    @Test
    void everyDroppedConstraintNameExistsInTheBaseline() throws Exception {
        final String v5 = resource("/db/migration/V5__enum_columns_drop_check_constraints.sql");
        final String baseline = resource("/db/migration/V1__baseline.sql");
        // Pin table and name together: a wrong table is a silent NOTICE, exactly the no-op
        // this migration must never become.
        final String[][] expected = {
                {"seasons", "seasons_status_check"},
                {"season_award_results", "season_award_results_category_check"},
                {"player_spotlights", "player_spotlights_insight_type_check"},
                {"comment_reactions", "comment_reactions_reaction_type_check"},
        };
        for (final String[] row : expected) {
            assertTrue(v5.contains("ALTER TABLE " + row[0] + " DROP CONSTRAINT IF EXISTS " + row[1] + ";"),
                    row[0] + "." + row[1] + " missing from V5");
            assertTrue(baseline.contains("CONSTRAINT " + row[1]), row[1] + " not in V1 baseline");
        }
    }

    private static String resource(String path) throws Exception {
        try (InputStream in = EnumConstraintMigrationTest.class.getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("missing " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
