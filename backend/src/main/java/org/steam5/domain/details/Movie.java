package org.steam5.domain.details;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@NoArgsConstructor
@AllArgsConstructor
public class Movie {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String thumbnail;
    private String webm;
    private String mp4;
    private String dashAv1;
    private String dashH264;
    private String hlsH264;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "app_id")
    @JsonIgnore
    private SteamAppDetail appId;
}
