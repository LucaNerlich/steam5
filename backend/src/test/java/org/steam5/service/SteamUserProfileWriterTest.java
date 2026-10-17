package org.steam5.service;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The avatar blurhash enqueue is a {@code @TransactionalEventListener(AFTER_COMMIT)}, so it only
 * fires when {@link SteamUserProfileWriter#apply(String, JsonNode)} actually owns a transaction.
 * Dropping the annotation would silently stop blurhashes from ever being generated while every
 * Mockito-level test stays green, so pin the annotation itself.
 */
class SteamUserProfileWriterTest {

    @Test
    void applyRunsInsideATransaction() throws NoSuchMethodException {
        final Method apply = SteamUserProfileWriter.class.getMethod("apply", String.class, JsonNode.class);

        final boolean transactional = apply.isAnnotationPresent(jakarta.transaction.Transactional.class)
                || apply.isAnnotationPresent(org.springframework.transaction.annotation.Transactional.class);

        assertTrue(transactional, "apply(String, JsonNode) must carry jakarta or Spring @Transactional: "
                + "BlurhashEnqueueListener only runs AFTER_COMMIT, inside this writer's transaction");
    }
}
