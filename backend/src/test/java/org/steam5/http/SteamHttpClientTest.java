package org.steam5.http;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Steam API response cap. Before this, `getForEntity(url, String.class)` buffered the whole
 * body before anything could look at its size, so one oversized or malformed upstream response
 * could pressure or exhaust the single backend JVM's heap.
 */
class SteamHttpClientTest {

    @Test
    void readsABodyAtTheCap() throws IOException {
        final byte[] atCap = new byte[(int) SteamHttpClient.MAX_RESPONSE_BYTES];

        final byte[] read = SteamHttpClient.readBounded(new ByteArrayInputStream(atCap), SteamHttpClient.MAX_RESPONSE_BYTES);

        assertEquals(atCap.length, read.length, "a body exactly at the cap is allowed");
    }

    @Test
    void rejectsABodyOverTheCap() {
        final byte[] overCap = new byte[(int) SteamHttpClient.MAX_RESPONSE_BYTES + 1];

        final IOException thrown = assertThrows(IOException.class,
                () -> SteamHttpClient.readBounded(new ByteArrayInputStream(overCap), SteamHttpClient.MAX_RESPONSE_BYTES));

        assertTrue(thrown.getMessage().contains("cap"), thrown.getMessage());
    }

    @Test
    void readsASmallBodyUnchanged() throws IOException {
        final byte[] small = "{\"response\":{\"apps\":[]}}".getBytes(StandardCharsets.UTF_8);

        assertArrayEquals(small, SteamHttpClient.readBounded(new ByteArrayInputStream(small), SteamHttpClient.MAX_RESPONSE_BYTES));
    }

    @Test
    void theCapIsBoundedAndSmallerThanTheJvmHeapHeadroom() {
        assertEquals(16L * 1024 * 1024, SteamHttpClient.MAX_RESPONSE_BYTES);
    }

    @Test
    void aChunkedResponseWithoutALengthIsStillCapped() throws IOException {
        // A stream that never ends: the read must stop at the cap instead of filling the heap.
        final InputStream endless = new InputStream() {
            @Override
            public int read() {
                return 'x';
            }
        };

        assertThrows(IOException.class, () -> SteamHttpClient.readBounded(endless, 1024L));
    }
}
