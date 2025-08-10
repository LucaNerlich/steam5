package org.steam5.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@ConfigurationProperties(prefix = "steam.apps")
public class SteamAppsConfig {

    private final String apiUrl = "https://api.steampowered.com/IStoreService/GetAppList/v1/";
    private final boolean includeGames = true;
    private final String downloadCron = "0 5 0 * * *"; // default: daily 00:05 UTC
    private final int httpTimeoutMs = 120_000;
    @Value("${STEAM_API_KEY}")
    private String apiKey;

}


