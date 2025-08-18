package org.steam5.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;

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

    @Column(name = "persona_name")
    private String personaName;

    @Column(name = "profile_url")
    private String profileUrl;

    @Column(name = "avatar")
    private String avatar;

    @Setter
    @Column(name = "blurhash_avatar", length = 200)
    private String blurhashAvatar;

    @Setter
    @Column(name = "blurdata_avatar", columnDefinition = "TEXT")
    private String blurdataAvatar;

    // not in use
    @Column(name = "avatar_medium")
    private String avatarMedium;

    @Column(name = "avatar_full")
    private String avatarFull;

    @Setter
    @Column(name = "blurhash_avatar_full", length = 200)
    private String blurhashAvatarFull;

    @Setter
    @Column(name = "blurdata_avatar_full", columnDefinition = "TEXT")
    private String blurdataAvatarFull;

    // not in use
    @Column(name = "avatar_hash")
    private String avatarHash;

    @Column(name = "last_logoff_sec")
    private Long lastLogoffSec;

    @Column(name = "persona_state")
    private Integer personaState;

    @Column(name = "primary_clan_id")
    private String primaryClanId;

    @Column(name = "time_created_sec")
    private Long timeCreatedSec;

    @Column(name = "country_code", length = 8)
    private String countryCode;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}


