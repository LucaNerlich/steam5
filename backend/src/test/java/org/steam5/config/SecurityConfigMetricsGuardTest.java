package org.steam5.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The metrics credential interlock is keyed on the credential itself: a present-but-empty
 * password beats the placeholder default, and a half-overridden pair must not pass as
 * "overridden" in a non-dev profile.
 */
class SecurityConfigMetricsGuardTest {

    private final SecurityConfig config = new SecurityConfig();
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    private MockEnvironment env(String... profiles) {
        final MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        return environment;
    }

    @Test
    void refusesToStartWhenThePasswordIsStillTheDefault() {
        assertThrows(IllegalStateException.class,
                () -> config.metricsUserDetailsService("metrics", "metrics", encoder, env("coolify")));
    }

    @Test
    void refusesToStartWhenThePasswordIsPresentButEmpty() {
        assertThrows(IllegalStateException.class,
                () -> config.metricsUserDetailsService("scraper", "", encoder, env("coolify")));
    }

    @Test
    void refusesToStartWhenOnlyTheUsernameWasOverridden() {
        assertThrows(IllegalStateException.class,
                () -> config.metricsUserDetailsService("scraper", "metrics", encoder, env("coolify")));
    }

    @Test
    void startsWhenOnlyTheUsernameStaysAtItsDefault() {
        // The username being "metrics" is harmless; only the password gates startup.
        final var manager = config.metricsUserDetailsService("metrics", "s3cret-scrape", encoder, env("coolify"));

        assertEquals("metrics", manager.loadUserByUsername("metrics").getUsername());
    }

    @Test
    void startsWithAnOverriddenPasswordOutsideDev() {
        final var manager = config.metricsUserDetailsService("scraper", "s3cret-scrape", encoder, env("coolify"));

        assertEquals("scraper", manager.loadUserByUsername("scraper").getUsername());
    }

    @Test
    void keepsStartingInTheDevProfileWithDefaults() {
        final var manager = config.metricsUserDetailsService("metrics", "metrics", encoder, env("dev"));

        assertEquals("metrics", manager.loadUserByUsername("metrics").getUsername());
    }
}
