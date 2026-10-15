package org.steam5.http;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.steam5.config.SteamAppsConfig;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class SteamHttpClient {

    private final RestTemplate restTemplate;
    private final SteamRateLimiter rateLimiter;
    private final MeterRegistry meterRegistry;

    public SteamHttpClient(SteamAppsConfig cfg, SteamRateLimiter rateLimiter, MeterRegistry meterRegistry) {
        this.rateLimiter = rateLimiter;
        this.meterRegistry = meterRegistry;
        final SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(cfg.getHttpTimeoutMs());
        rf.setReadTimeout(cfg.getHttpTimeoutMs());
        this.restTemplate = new RestTemplate(rf);
    }

    private static boolean shouldRetry(int status) {
        // Retry transient server errors; never retry 429 to respect rate limiting
        return status == 502 || status == 503 || status == 504 || status == 500;
    }

    private void backoff(int attempt) {
        // Exponential backoff: 250ms, 750ms
        final long sleepMs = 250L * (long) Math.pow(3, Math.max(0, attempt - 1));
        log.debug("Backoff for {}ms before retry attempt {}", sleepMs, attempt + 1);
        try {
            Thread.sleep(sleepMs);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    // Strip API key from URL before logging to prevent exposure in logs
    public static String sanitizeUrl(String url) {
        if (url == null) return null;
        return url.replaceAll("key=[^&]+", "key=***");
    }

    // Map a request URL to a bounded `endpoint` tag value. Anything not
    // recognised falls into `other` so the metric label cardinality stays
    // fixed regardless of which job emits the request.
    private static String endpointTag(String url) {
        if (url == null) return "other";
        if (url.contains("/api/appdetails")) return "appDetails";
        if (url.contains("/appreviews/")) return "appReviews";
        if (url.contains("GetAppList")) return "appList";
        if (url.contains("/openid/")) return "openid";
        return "other";
    }

    private void recordOutcome(String endpoint, String outcome, long elapsedNanos) {
        Counter.builder("steam.api.requests")
                .description("Steam API requests by endpoint and outcome")
                .tags(Tags.of("endpoint", endpoint, "outcome", outcome))
                .register(meterRegistry)
                .increment();
        Timer.builder("steam.api.request.duration")
                .description("Steam API request duration")
                .tags(Tags.of("endpoint", endpoint, "outcome", outcome))
                .register(meterRegistry)
                .record(elapsedNanos, java.util.concurrent.TimeUnit.NANOSECONDS);
    }

    private void recordRateLimited(String endpoint) {
        Counter.builder("steam.api.rate.limit.hits")
                .description("Steam API responses observed with HTTP 429")
                .tags(Tags.of("endpoint", endpoint))
                .register(meterRegistry)
                .increment();
    }

    public String get(String url) throws SteamApiException {
        final int maxAttempts = 3;
        final String endpoint = endpointTag(url);
        final long started = System.nanoTime();
        int attempt = 0;
        while (true) {
            attempt++;
            try {
                rateLimiter.acquirePermit();
                final String body = readBodyBounded(url);
                if (attempt > 1) {
                    log.info("Steam API call succeeded on attempt {}: {}", attempt, sanitizeUrl(url));
                }
                recordOutcome(endpoint, "success", System.nanoTime() - started);
                return body;
            } catch (RestClientResponseException rcre) {
                final int status = rcre.getStatusCode().value();
                if (shouldRetry(status) && attempt < maxAttempts) {
                    log.warn("Steam API exception {} on attempt {}/{}, retrying: {}",
                             status, attempt, maxAttempts, sanitizeUrl(url));
                    backoff(attempt);
                    continue;
                }
                log.error("Steam API call failed after {} attempts: status={} url={}",
                          attempt, status, sanitizeUrl(url));
                if (status == 429) recordRateLimited(endpoint);
                recordOutcome(endpoint, status == 429 ? "rate_limited" : "failed", System.nanoTime() - started);
                throw new SteamApiException(status, url, "HTTP " + status + " calling Steam API", rcre);
            } catch (ResourceAccessException rae) {
                // I/O errors or timeouts; retry with backoff
                if (attempt < maxAttempts) {
                    log.warn("Steam API network error on attempt {}/{}, retrying: {} ({})",
                             attempt, maxAttempts, sanitizeUrl(url), rae.getMessage());
                    backoff(attempt);
                    continue;
                }
                log.error("Steam API network error after {} attempts: url={}", attempt, sanitizeUrl(url), rae);
                recordOutcome(endpoint, "network_error", System.nanoTime() - started);
                throw new SteamApiException(599, url, "Network error calling Steam API", rae);
            }
        }
    }

    /** Largest Steam API response this client will buffer. */
    static final long MAX_RESPONSE_BYTES = 16L * 1024 * 1024;

    /**
     * GETs {@code url} and reads the body with a hard size cap.
     *
     * <p>{@code getForEntity(url, String.class)} buffered the entire response before anything
     * could look at its size, so one oversized or malformed Steam response could pressure or
     * exhaust the single backend JVM's heap (the app list request alone asks Steam for up to
     * 50k apps). The declared {@code Content-Length} is rejected first, and the read is capped
     * as well because a chunked response declares no length.</p>
     */
    private String readBodyBounded(final String url) {
        return restTemplate.execute(url, HttpMethod.GET, null, response -> {
            final HttpStatusCode status = response.getStatusCode();
            if (!status.is2xxSuccessful()) {
                // The default error handler throws for 4xx/5xx; only 1xx or an unfollowed 3xx
                // reach here, and they must not be buffered as a success.
                throw new SteamApiException(status.value(), url, "HTTP " + status.value() + " calling Steam API");
            }
            final long declared = response.getHeaders().getContentLength();
            if (declared > MAX_RESPONSE_BYTES) {
                throw new IOException("Steam API response declares " + declared
                        + " bytes, over the " + MAX_RESPONSE_BYTES + "-byte cap");
            }
            return new String(readBounded(response.getBody(), MAX_RESPONSE_BYTES), StandardCharsets.UTF_8);
        });
    }

    /**
     * Reads at most {@code maxBytes} from {@code in}, failing when the stream holds more.
     *
     * @param in       the response body stream
     * @param maxBytes the largest body this client will buffer
     * @return the body bytes
     * @throws IOException when the body exceeds {@code maxBytes} (or the stream fails)
     */
    static byte[] readBounded(final InputStream in, final long maxBytes) throws IOException {
        final byte[] body = in.readNBytes((int) maxBytes + 1);
        if (body.length > maxBytes) {
            throw new IOException("Steam API response exceeds the " + maxBytes + "-byte cap");
        }
        return body;
    }
}
