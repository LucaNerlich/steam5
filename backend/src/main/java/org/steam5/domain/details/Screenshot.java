package org.steam5.domain.details;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "screenshots")
public class Screenshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "app_id")
    @JsonIgnore
    private SteamAppDetail appId;

    @Column(name = "path_thumbnail")
    private String pathThumbnail;

    @Column(name = "path_full")
    private String pathFull;

    @Setter
    @Column(name = "blurhash_thumb", length = 200)
    private String blurhashThumb;

    @Setter
    @Column(name = "blurhash_full", length = 200)
    private String blurhashFull;

}
