package org.steam5.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Constants and credentials shared by the Steam API fetchers. The values are plain
 * constants with no setters, so this class deliberately carries no
 * {@code @ConfigurationProperties} binding: there is no {@code steam.apps.*} key to set.
 */
@Getter
@Configuration
public class SteamAppsConfig {

    private final String apiUrl = "https://api.steampowered.com/IStoreService/GetAppList/v1/";
    private final int httpTimeoutMs = 120_000;
    @Value("${STEAM_API_KEY}")
    private String apiKey;

}


