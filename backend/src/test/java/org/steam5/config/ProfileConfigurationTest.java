package org.steam5.config;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The active Spring profile must not have a default.
 *
 * <p>A fallback of {@code dev} meant a deployment that forgot {@code SPRING_PROFILE} silently
 * ran the dev profile, which exposes extra actuator endpoints and downgrades the
 * "refusing to start on the public default JWT secret" interlock to a warning — i.e. the
 * guards failed open. This pins the absence of that fallback; the unit suite never boots a
 * Spring context, so nothing else would notice it coming back.</p>
 */
class ProfileConfigurationTest {

    @Test
    @SuppressWarnings("unchecked")
    void theActiveProfileHasNoDefaultSoAForgottenVariableCannotEnableDev() throws Exception {
        final Map<String, Object> config;
        try (InputStream in = ProfileConfigurationTest.class.getResourceAsStream("/application.yml")) {
            assertNotNull(in, "application.yml must be on the test classpath");
            config = new Yaml().load(in);
        }

        final Map<String, Object> spring = (Map<String, Object>) config.get("spring");
        final Map<String, Object> profiles = (Map<String, Object>) spring.get("profiles");

        assertEquals("${SPRING_PROFILE}", profiles.get("active"),
                "spring.profiles.active must have no default: a fallback silently enables the dev profile");
    }
}
