package org.steam5.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "steam_app_index")
public class SteamAppIndex {

    @Id
    @Column(name = "app_id", nullable = false)
    private Long appId;

    @Column(name = "name", nullable = false)
    private String name;

}


