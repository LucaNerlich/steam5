package org.steam5.domain.details;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "steam_app_details")
public class SteamAppDetail {

    @Id
    @Column(name = "app_id", nullable = false)
    private Long appId;

    private String type;

    @Column(name = "is_free")
    private boolean isFree;

    // comma separated list of DLC appIds
    private String dlc;

    @Column(name = "short_description")
    private String shortDescription;
    @Column(name = "detailed_description")
    private String detailedDescription;
    @Column(name = "about_the_game")
    private String aboutTheGame;
    @Column(name = "header_image")
    private String headerImage;
    @Column(name = "capsule_image")
    private String capsuleImage;
    private String website;
    @Column(name = "legal_notice")
    private String legalNotice;


    @OneToMany(mappedBy = "appId", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Developer> developers = new ArrayList<>();

    @OneToMany(mappedBy = "appId", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Publisher> publisher = new ArrayList<>();

    private boolean isWindows;
    private boolean isMac;
    private boolean isLinux;

    @OneToMany(mappedBy = "appId", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Genre> genres = new ArrayList<>();

    @OneToMany(mappedBy = "appId", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Screenshot> screenshots = new ArrayList<>();

    @OneToMany(mappedBy = "appId", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Movie> movies = new ArrayList<>();

    // mapped by field -> "total"
    private Long recommendations;

    // mapped by field -> "release_date.date"
    @Column(name = "release_date")
    private String releaseDate;

    @Column(name = "background_raw")
    private String backgroundRaw;

}
