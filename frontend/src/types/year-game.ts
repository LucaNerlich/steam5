import type {SteamAppDetail} from '@/types/review-game';

export interface HintTierMeta {
    level: number;
    label: string;
    description: string;
    maxPoints: number;
}

export interface YearGameState {
    date: string;
    hintTiers: HintTierMeta[];
    picks: SteamAppDetail[];
}

export interface GuessRequest {
    appId: number;
    guessYear: number;
}

export interface GuessResponse {
    appId: number;
    guessYear: number;
    correct: boolean;
    distance: number;
    releaseYear: number | null;
    hintsUsed: number;
    maxPoints: number;
    unlockableHintLevels: number[];
    points: number | null;
}

export interface HintRequest {
    appId: number;
    hintLevel: number;
}

export interface HintResponse {
    hintLevel: number;
    content: string;
    hintsUsed: number;
    maxPoints: number;
}

export interface MyYearGuess {
    roundIndex: number;
    appId: number;
    guessedYear: number | null;
    actualYear: number | null;
    hintsUsed: number;
    bestDistance: number | null;
    unlockableHintLevels: number[];
    completed: boolean;
    points: number;
}

export interface HintMetaResponse {
    distanceThresholds: number[];
    tiers: HintTierMeta[];
}
