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
@Table(name = "steam_app_reviews")
public class SteamAppReviews {

    @Id
    @Column(name = "app_id", nullable = false)
    private Long appId;

    @Column(name = "total_positive", nullable = false)
    private int totalPositive;

    @Column(name = "total_negative", nullable = false)
    private int totalNegative;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
