package org.steam5.http;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.steam5.config.SteamAppsConfig;
import org.steam5.service.SteamRateLimiter;

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

    public String get(String url) {
        rateLimiter.acquirePermit();
        return restTemplate.getForObject(url, String.class);
    }
}


