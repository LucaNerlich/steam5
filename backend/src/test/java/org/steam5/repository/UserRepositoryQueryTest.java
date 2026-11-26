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
        final String sql = query.value().toLowerCase(java.util.Locale.ROOT).replaceAll("\\s+", " ");

        assertTrue(sql.contains("on conflict (steam_id) do update"), sql);
        // Pin both clauses whole: a blocklist of profile column names missed persona_state,
        // country_code and friends, and never checked the insert's columns or value order.
        assertTrue(sql.contains("insert into users (steam_id, created_at, updated_at, token_not_valid_before) "
                + "values (:steamid, :now, :now, :cutoff)"), sql);
        assertTrue(sql.contains("set token_not_valid_before = greatest(users.token_not_valid_before, "
                + "excluded.token_not_valid_before), updated_at = excluded.updated_at"), sql);
        assertFalse(sql.contains("update users set"), "a whole-row update is what #695 was");
    }
}
