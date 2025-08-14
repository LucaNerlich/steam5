package org.steam5.domain.details;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "price")
public class Price {

    @Id
    @Column(name = "app_id")
    private Long appId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "app_id")
    @JsonIgnore
    private SteamAppDetail app;

    private String currency;

    private long initial;

    @Column(name = "final")
    private long finalAmount;

    @Column(name = "discount_percent")
    private int discountPercent;

    @Column(name = "initial_formatted")
    private String initialFormatted;

    @Column(name = "final_formatted")
    private String finalFormatted;
}
