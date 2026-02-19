package org.steam5.http;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.steam5.config.SteamAppsConfig;

@Slf4j
@Component
public class SteamHttpClient {

    private final RestTemplate restTemplate;
    private final SteamRateLimiter rateLimiter;

    public SteamHttpClient(SteamAppsConfig cfg, SteamRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
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
    private static String sanitizeUrl(String url) {
        return url.replaceAll("key=[^&]+", "key=***");
    }

    public String get(String url) throws SteamApiException {
        final int maxAttempts = 3;
        int attempt = 0;
        while (true) {
            attempt++;
            try {
                rateLimiter.acquirePermit();
                final ResponseEntity<String> resp = restTemplate.getForEntity(url, String.class);
                final int status = resp.getStatusCode().value();
                if (status >= 200 && status < 300) {
                    if (attempt > 1) {
                        log.info("Steam API call succeeded on attempt {}: {}", attempt, sanitizeUrl(url));
                    }
                    return resp.getBody();
                }
                // Non-2xx without exception (custom handlers) - treat as error
                if (shouldRetry(status) && attempt < maxAttempts) {
                    log.warn("Steam API returned {} on attempt {}/{}, retrying: {}",
                             status, attempt, maxAttempts, sanitizeUrl(url));
                    backoff(attempt);
                    continue;
                }
                throw new SteamApiException(status, url, "HTTP " + status + " calling Steam API");
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
                throw new SteamApiException(599, url, "Network error calling Steam API", rae);
            }
        }
    }
}


