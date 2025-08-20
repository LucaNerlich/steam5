package org.steam5.domain.details;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

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
    private String name;

    @Column(name = "controller_support")
    private String controllerSupport;

    @Column(name = "is_free")
    private boolean isFree;

    // comma separated list of DLC appIds
    @Column(columnDefinition = "TEXT")
    private String dlc;

    @Column(name = "short_description", columnDefinition = "TEXT")
    private String shortDescription;
    @Column(name = "detailed_description", columnDefinition = "TEXT")
    private String detailedDescription;
    @Column(name = "about_the_game", columnDefinition = "TEXT")
    private String aboutTheGame;
    @Column(name = "header_image", columnDefinition = "TEXT")
    private String headerImage;
    @Column(name = "capsule_image", columnDefinition = "TEXT")
    private String capsuleImage;
    @Column(columnDefinition = "TEXT")
    private String website;
    @Column(name = "legal_notice", columnDefinition = "TEXT")
    private String legalNotice;

    @OneToOne(mappedBy = "app", cascade = CascadeType.ALL, orphanRemoval = true)
    private Price priceOverview;

    @ManyToMany
    @JoinTable(
            name = "steam_app_developer",
            joinColumns = @JoinColumn(name = "app_id"),
            inverseJoinColumns = @JoinColumn(name = "developer_id")
    )
    private Set<Developer> developers = new HashSet<>();

    @ManyToMany
    @JoinTable(
            name = "steam_app_publisher",
            joinColumns = @JoinColumn(name = "app_id"),
            inverseJoinColumns = @JoinColumn(name = "publisher_id")
    )
    private Set<Publisher> publisher = new HashSet<>();

    @ManyToMany
    @JoinTable(
            name = "steam_app_category",
            joinColumns = @JoinColumn(name = "app_id"),
            inverseJoinColumns = @JoinColumn(name = "category_id")
    )
    private Set<Category> categories = new HashSet<>();

    private boolean isWindows;
    private boolean isMac;
    private boolean isLinux;

    @ManyToMany
    @JoinTable(
            name = "steam_app_genre",
            joinColumns = @JoinColumn(name = "app_id"),
            inverseJoinColumns = @JoinColumn(name = "genre_id")
    )
    private Set<Genre> genres = new HashSet<>();

    @OneToMany(mappedBy = "appId", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Screenshot> screenshots = new HashSet<>();

    @OneToMany(mappedBy = "appId", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Movie> movies = new HashSet<>();

    // mapped by field -> "total"
    private Long recommendations;

    // mapped by field -> "release_date.date"
    @Column(name = "release_date")
    private String releaseDate;

    @Column(name = "background_raw", columnDefinition = "TEXT")
    private String backgroundRaw;

}
