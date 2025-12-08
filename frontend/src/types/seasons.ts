export type AwardView = {
    category: string;
    categoryLabel: string;
    placementLevel: number;
    steamId: string;
    personaName: string;
    avatar?: string | null;
    avatarBlurHash?: string | null;
    metricValue: number;
    tiebreakRoll?: number | null;
};

export type SeasonView = {
    id: number;
    seasonNumber: number;
    startDate: string;
    endDate: string;
    status: "PLANNED" | "ACTIVE" | "FINALIZED";
    awardsFinalizedAt?: string | null;
    awards: AwardView[];
};

export type SeasonSummary = {
    totalPlayers: number;
    totalRounds: number;
    totalPoints: number;
    totalHits: number;
    averagePointsPerRound: number;
    averagePointsPerPlayer: number;
    hitRate: number;
    averageActiveDays: number;
    longestStreak: number;
    durationDays: number;
    completedDays: number;
    dataThrough?: string | null;
};

export type PlayerHighlight = {
    rank: number;
    steamId: string;
    personaName: string;
    avatar?: string | null;
    avatarBlurHash?: string | null;
    profileUrl?: string | null;
    totalPoints: number;
    rounds: number;
    hits: number;
    avgPointsPerRound: number;
    avgPointsPerDay: number;
    activeDays: number;
    longestStreak: number;
};

export type DailyHighlight = {
    date: string;
    avgScore: number;
    playerCount: number;
};

export type SeasonDailyHighlights = {
    highestAvg?: DailyHighlight | null;
    lowestAvg?: DailyHighlight | null;
    busiest?: DailyHighlight | null;
} | null;

export type SeasonDetailResponse = {
    season: SeasonView;
    summary: SeasonSummary;
    topPlayers: PlayerHighlight[];
    highlights: SeasonDailyHighlights;
};

