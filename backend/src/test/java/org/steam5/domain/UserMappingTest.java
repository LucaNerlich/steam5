package org.steam5.domain;

import org.hibernate.annotations.DynamicUpdate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class UserMappingTest {

    @Test
    void userEmitsOnlyChangedColumns() {
        // SteamUserProfileWriter save()s a managed entity; without @DynamicUpdate the UPDATE
        // carries every column from the snapshot, reverting a concurrent logout's
        // token_not_valid_before (and the avatar job's blurhash write).
        assertNotNull(User.class.getAnnotation(DynamicUpdate.class),
                "User must stay @DynamicUpdate so whole-entity saves cannot revert column-scoped writes");
    }
}
