package org.steam5.http;

import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.steam5.config.SteamAppsConfig;

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

    private static void backoff(int attempt) {
        // Exponential backoff: 250ms, 750ms
        final long sleepMs = 250L * (long) Math.pow(3, Math.max(0, attempt - 1));
        try {
            Thread.sleep(sleepMs);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
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
                    return resp.getBody();
                }
                // Non-2xx without exception (custom handlers) - treat as error
                if (shouldRetry(status) && attempt < maxAttempts) {
                    backoff(attempt);
                    continue;
                }
                throw new SteamApiException(status, url, "HTTP " + status + " calling Steam API");
            } catch (RestClientResponseException rcre) {
                final int status = rcre.getStatusCode().value();
                if (shouldRetry(status) && attempt < maxAttempts) {
                    backoff(attempt);
                    continue;
                }
                throw new SteamApiException(status, url, "HTTP " + status + " calling Steam API");
            } catch (ResourceAccessException rae) {
                // I/O errors or timeouts; retry with backoff
                if (attempt < maxAttempts) {
                    backoff(attempt);
                    continue;
                }
                throw new SteamApiException(599, url, "Network error calling Steam API");
            }
        }
    }
}


