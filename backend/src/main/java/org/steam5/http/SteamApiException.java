package org.steam5.http;

import lombok.Getter;

import java.io.IOException;

@Getter
public class SteamApiException extends IOException {

    private final int statusCode;
    private final String url;

    public SteamApiException(int statusCode, String url, String message) {
        super(message);
        this.statusCode = statusCode;
        // Fix: never retain the raw URL — it carries key=<apiKey> and any
        // consumer that logs this exception would leak the Steam API key.
        this.url = SteamHttpClient.sanitizeUrl(url);
    }

    public SteamApiException(int statusCode, String url, String message, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.url = SteamHttpClient.sanitizeUrl(url);
    }
}
