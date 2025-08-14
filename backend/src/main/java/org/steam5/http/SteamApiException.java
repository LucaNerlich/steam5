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
        this.url = url;
    }
}


