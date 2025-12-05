package org.steam5.domain;

import lombok.Getter;

@Getter
public enum SeasonAwardCategory {
    MOST_POINTS("Most Points"),
    MOST_HITS("Most Hits"),
    HIGHEST_AVG_POINTS_PER_DAY("Highest Avg Points / Day"),
    LONGEST_STREAK("Longest Streak");

    private final String label;

    SeasonAwardCategory(String label) {
        this.label = label;
    }
}


