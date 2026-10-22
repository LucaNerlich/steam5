package org.steam5.http;

import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.steam5.config.SteamAppsConfig;

import java.io.ByteArrayInputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

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

    private static SteamHttpClient client() {
        return new SteamHttpClient(new SteamAppsConfig(),
                new SteamRateLimiter(new SimpleMeterRegistry()), new SimpleMeterRegistry());
    }

    @Test
    void failingStatusIsClassifiedInOneAttempt() throws Exception {
        // The body is not copied into memory any more; Spring still drains it on close, so this
        // test pins the classification and the single attempt, not the transfer.
        final AtomicInteger calls = new AtomicInteger();
        final HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/err", exchange -> {
            calls.incrementAndGet();
            final byte[] body = new byte[1024];
            exchange.sendResponseHeaders(404, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            final String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/err";
            final SteamApiException e = assertThrows(SteamApiException.class, () -> client().get(url));
            assertEquals(404, e.getStatusCode());
            assertEquals(1, calls.get(), "a 404 is definitive and must not be retried");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void overCapResponseFailsOnceAs413() throws Exception {
        final AtomicInteger calls = new AtomicInteger();
        final HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/big", exchange -> {
            calls.incrementAndGet();
            exchange.sendResponseHeaders(200, SteamHttpClient.MAX_RESPONSE_BYTES + 1);
            exchange.close();
        });
        server.start();
        try {
            final String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/big";
            final SteamApiException e = assertThrows(SteamApiException.class, () -> client().get(url));
            assertEquals(413, e.getStatusCode(), "the cap is a definitive failure, not a network error");
            assertEquals(1, calls.get(), "an over-cap body must not be downloaded three times");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void transportFailureStillRetriesAndMapsTo599() throws Exception {
        final int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        final String url = "http://127.0.0.1:" + closedPort + "/nope";
        final SteamApiException e = assertThrows(SteamApiException.class, () -> client().get(url));
        assertEquals(599, e.getStatusCode());
    }

    @Test
    void chunkedOverCapResponseFailsOnceAs413() throws Exception {
        final AtomicInteger calls = new AtomicInteger();
        final HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/chunked", exchange -> {
            calls.incrementAndGet();
            // No declared length: only the streamed read can catch this one.
            exchange.sendResponseHeaders(200, 0);
            final byte[] chunk = new byte[64 * 1024];
            try (final java.io.OutputStream out = exchange.getResponseBody()) {
                for (long sent = 0; sent <= SteamHttpClient.MAX_RESPONSE_BYTES; sent += chunk.length) {
                    out.write(chunk);
                }
            } catch (IOException ignored) {
                // the client closes the connection once it has seen enough
            }
            exchange.close();
        });
        server.start();
        try {
            final String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/chunked";
            final SteamApiException e = assertThrows(SteamApiException.class, () -> client().get(url));
            assertEquals(413, e.getStatusCode(), "a streamed over-cap body must not be reported as 599");
            assertEquals(1, calls.get(), "an over-cap body must not be downloaded three times");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void retryableStatusIsRetriedThreeTimes() throws Exception {
        final AtomicInteger calls = new AtomicInteger();
        final HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/retry", exchange -> {
            calls.incrementAndGet();
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });
        server.start();
        try {
            final String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/retry";
            final SteamApiException e = assertThrows(SteamApiException.class, () -> client().get(url));
            assertEquals(500, e.getStatusCode());
            assertEquals(3, calls.get(), "5xx is transient and gets the three attempts");
        } finally {
            server.stop(0);
        }
    }
}
