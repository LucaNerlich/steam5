package org.steam5.repository;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The upsert is native SQL, so a wrong conflict target or a dropped greatest() is invisible to
 * the service test (it mocks the repository). Pin the statement itself.
 */
class UserRepositoryQueryTest {

    @Test
    void logoutUpsertOnlyWritesTheCutoffColumns() throws Exception {
        final Method method = UserRepository.class.getMethod(
                "upsertTokenNotValidBefore", String.class, OffsetDateTime.class, OffsetDateTime.class);
        final Query query = method.getAnnotation(Query.class);
        assertTrue(query != null && query.nativeQuery(), "must stay a native upsert");
        final String sql = query.value().toLowerCase();

        assertTrue(sql.contains("on conflict (steam_id) do update"), sql);
        assertTrue(sql.contains("greatest(users.token_not_valid_before, excluded.token_not_valid_before)"), sql);
        assertTrue(sql.contains("insert into users"), sql);
        // Profile columns must never appear in the update list; a whole-row write is what #695 was.
        for (final String column : new String[]{
                "persona_name", "profile_url", "avatar", "created_at =", "blurhash",
        }) {
            assertFalse(sql.contains(column), column + " must not be written by the logout upsert");
        }
    }
}
