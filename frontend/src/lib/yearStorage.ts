export type RevealedHint = {
    level: number;
    content: string;
};

export type YearRoundProgress = {
    appId: number;
    pickName?: string;
    hintsUsed: number;
    revealedHints: RevealedHint[];
    unlockableHintLevels: number[];
    lastDistance?: number;
    lastGuessYear?: number;
    lastGuessTooEarly?: boolean;
    completed: boolean;
    actualYear?: number;
    points?: number;
};

export type StoredYearDay = {
    totalRounds: number;
    rounds: Record<number, YearRoundProgress>;
};

function storageKeyForDate(gameDate: string): string {
    return `year-guesser:${gameDate}`;
}

export function loadYearDay(gameDate: string): StoredYearDay | null {
    try {
        const raw = typeof window !== 'undefined' ? window.localStorage.getItem(storageKeyForDate(gameDate)) : null;
        return raw ? (JSON.parse(raw) as StoredYearDay) : null;
    } catch {
        return null;
    }
}

export function saveYearRound(gameDate: string, roundIndex: number, totalRounds: number, data: YearRoundProgress): StoredYearDay | null {
    try {
        const key = storageKeyForDate(gameDate);
        const prev = loadYearDay(gameDate) ?? {totalRounds, rounds: {}};
        prev.totalRounds = totalRounds;
        prev.rounds[roundIndex] = data;
        window.localStorage.setItem(key, JSON.stringify(prev));
        return prev;
    } catch {
        return null;
    }
}
