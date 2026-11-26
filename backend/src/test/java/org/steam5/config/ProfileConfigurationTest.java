package org.steam5.config;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    @SuppressWarnings("unchecked")
    void theBaseRedirectBaseIsTheProductionOriginAndDevKeepsLocalhost() throws Exception {
        // The localhost fallback lived in the base profile: a deployment that forgot
        // REDIRECT_BASE sent every login and its OpenID assertion to the visitor's machine.
        final Map<String, Object> base = yaml("/application.yml");
        final Map<String, Object> auth = (Map<String, Object>) base.get("auth");
        assertEquals("${REDIRECT_BASE:https://steam5.org}", auth.get("redirectBase"));

        final Map<String, Object> dev = yaml("/application-dev.yml");
        final Map<String, Object> devAuth = (Map<String, Object>) dev.get("auth");
        assertNotNull(devAuth, "application-dev.yml must keep the local login convenience");
        assertEquals("${REDIRECT_BASE:http://localhost:3000}", devAuth.get("redirectBase"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void theBaseProfileDoesNotOverrideBootsErrorMessageDefault() throws Exception {
        // include-message: always re-opened the disclosure GlobalExceptionHandler closes, on
        // every error path outside the advice.
        final Map<String, Object> base = yaml("/application.yml");
        final Map<String, Object> spring = (Map<String, Object>) base.get("spring");
        final Map<String, Object> web = spring == null ? null : (Map<String, Object>) spring.get("web");
        final Map<String, Object> error = web == null ? null : (Map<String, Object>) web.get("error");
        assertTrue(error == null || !error.containsKey("include-message"),
                "include-message must stay at Boot's `never` default");
    }

    @Test
    @SuppressWarnings("unchecked")
    void theBaseProfileKeepsJacksonAtBootsDefaults() throws Exception {
        // indent-output and map-key ordering are the kind of "readable locally" setting that
        // gets re-added to the base profile; nothing else notices the production cost.
        final Map<String, Object> base = yaml("/application.yml");
        final Map<String, Object> spring = (Map<String, Object>) base.get("spring");
        final Map<String, Object> jackson = spring == null ? null : (Map<String, Object>) spring.get("jackson");
        assertTrue(jackson == null || jackson.get("serialization") == null,
                "spring.jackson.serialization must stay at Boot's defaults");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> yaml(String resource) throws Exception {
        try (InputStream in = ProfileConfigurationTest.class.getResourceAsStream(resource)) {
            assertNotNull(in, resource + " must be on the test classpath");
            return new Yaml().load(in);
        }
    }
}
