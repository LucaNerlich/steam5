package org.steam5.http;

import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
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

    public String get(String url) throws SteamApiException {
        rateLimiter.acquirePermit();
        ResponseEntity<String> resp = restTemplate.getForEntity(url, String.class);
        int status = resp.getStatusCode().value();
        if (status >= 200 && status < 300) {
            return resp.getBody();
        }
        throw new SteamApiException(status, url, "HTTP " + status + " calling Steam API");
    }
}


