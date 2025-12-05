package org.steam5.domain;

import lombok.Getter;

@Getter
public enum SeasonAwardCategory {
    MOST_POINTS("Most Points"),
    MOST_HITS("Most Hits");

    private final String label;

    SeasonAwardCategory(String label) {
        this.label = label;
    }
}


