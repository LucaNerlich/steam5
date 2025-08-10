package org.steam5.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Getter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "ingest_state")
public class IngestState {

    @Id
    @Column(name = "id", nullable = false)
    private String id;

    @Column(name = "last_app_id", nullable = false)
    private long lastAppId;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

}


