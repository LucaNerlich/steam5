package org.steam5.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Entity
@Table(name = "users", uniqueConstraints = @UniqueConstraint(columnNames = {"steam_id"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @Column(name = "steam_id", nullable = false, length = 32)
    private String steamId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();
}


