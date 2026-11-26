package org.steam5.repository;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the bucket-edge formula behind #710 without a database harness (the repo has no
 * Testcontainers setup): the SQL is built inline, so read the source and assert the shape.
 * A CEIL width pushed the last bucket's lower edge past its upper edge on small ranges, and
 * the open-ended LINEAR_WINSORIZED bucket inverted the same way before the WHERE filter.
 */
class ReviewsBucketRepositoryQueryTest {

    @Test
    void equalWidthDerivesEdgesAndDropsEmptyBuckets() throws Exception {
        final String source = source();
        assertTrue(source.contains("((ext.mx - ext.mn + 1) * i) / 5"), "edge formula changed");
        assertFalse(source.contains("CEIL(((ext.mx - ext.mn + 1)"), "CEIL width reintroduced");
        assertTrue(source.contains("WHERE lower <= upper"), "empty buckets are emitted again");
    }

    @Test
    void linearWinsorizedKeepsTheOpenEndedBucketAndDropsEmptyOnes() throws Exception {
        final String source = source();
        assertTrue(source.contains("WHERE upper IS NULL OR lower <= upper"),
                "open-ended bucket filter lost");
        assertFalse(source.contains("CEIL(((ext.cap - ext.mn + 1)"), "CEIL width reintroduced");
    }

    private static String source() throws Exception {
        return Files.readString(Path.of("src/main/java/org/steam5/repository/ReviewsBucketRepository.java"));
    }
}
