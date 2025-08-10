package org.steam5.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import org.steam5.domain.IngestState;

import java.time.OffsetDateTime;

public interface IngestStateRepository extends JpaRepository<IngestState, String> {

    @Transactional
    @Modifying(clearAutomatically = false, flushAutomatically = false)
    @Query(value = "INSERT INTO ingest_state(id, last_app_id, updated_at) VALUES (:id, :lastAppId, :updatedAt) " +
            "ON CONFLICT (id) DO UPDATE SET last_app_id = EXCLUDED.last_app_id, updated_at = EXCLUDED.updated_at",
            nativeQuery = true)
    void upsert(@Param("id") String id,
               @Param("lastAppId") long lastAppId,
               @Param("updatedAt") OffsetDateTime updatedAt);
}


