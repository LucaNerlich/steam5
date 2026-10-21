export type RoundResult = {
    appId: number;
    pickName?: string;
    selectedLabel: string;
    actualBucket: string;
    totalReviews: number;
    correct: boolean;
};

export type StoredDay = { totalRounds: number; results: Record<number, RoundResult> };

function storageKeyForDate(gameDate: string): string {
    return `review-guesser:${gameDate}`;
}

export function loadDay(gameDate: string): StoredDay | null {
    try {
        const raw = typeof window !== 'undefined' ? window.localStorage.getItem(storageKeyForDate(gameDate)) : null;
        return raw ? (JSON.parse(raw) as StoredDay) : null;
    } catch {
        return null;
    }
}

export function saveRound(gameDate: string, roundIndex: number, totalRounds: number, data: RoundResult): StoredDay | null {
    try {
        const key = storageKeyForDate(gameDate);
        const prev = loadDay(gameDate) ?? {totalRounds, results: {}};
        prev.totalRounds = totalRounds;
        prev.results[roundIndex] = data;
        window.localStorage.setItem(key, JSON.stringify(prev));
        return prev;
    } catch {
        return null;
    }
}

const STORAGE_PREFIX = 'review-guesser:';

function todayUtc(): string {
    return new Date().toISOString().slice(0, 10);
}

/** Archive keys are `review-guesser:<yyyy-mm-dd>`; ISO dates compare lexicographically. */
function isArchivedKey(key: string, beforeDate: string): boolean {
    return key.startsWith(STORAGE_PREFIX) && key.slice(STORAGE_PREFIX.length) < beforeDate;
}

/**
 * Removes stored archive days, never the live round's key: the archive controls only ever
 * show past dates, so wiping today's progress is outside their stated scope.
 */
export function clearAll(beforeDate: string = todayUtc()): void {
    try {
        const toDelete: string[] = [];
        for (let i = 0; i < localStorage.length; i++) {
            const k = localStorage.key(i);
            if (k && isArchivedKey(k, beforeDate)) toDelete.push(k);
        }
        toDelete.forEach(k => localStorage.removeItem(k));
    } catch {
    }
}

export function clearDay(gameDate: string): void {
    try {
        const key = storageKeyForDate(gameDate);
        window.localStorage.removeItem(key);
    } catch {
    }
}

export function hasAny(beforeDate: string = todayUtc()): boolean {
    try {
        for (let i = 0; i < localStorage.length; i++) {
            const k = localStorage.key(i);
            if (k && isArchivedKey(k, beforeDate)) return true;
        }
        return false;
    } catch {
        return false;
    }
}


