package org.steam5.game;

public enum GameId {
    REVIEW_GUESSER("review-game"),
    RELEASE_YEAR_GUESSER("year-game"),
    PRICE_GUESSER("price-game");

    private final String apiSlug;

    GameId(final String apiSlug) {
        this.apiSlug = apiSlug;
    }

    public String getApiSlug() {
        return apiSlug;
    }

    public String cacheName() {
        return apiSlug;
    }
}
