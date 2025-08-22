export type RoundResult = {
    appId: number;
    pickName?: string;
    selectedLabel: string;
    actualBucket: string;
    totalReviews: number;
    correct: boolean;
};

export type StoredDay = { totalRounds: number; results: Record<number, RoundResult> };

export function storageKeyForDate(gameDate: string): string {
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

export function clearAll(): void {
    try {
        const toDelete: string[] = [];
        for (let i = 0; i < localStorage.length; i++) {
            const k = localStorage.key(i);
            if (k && k.startsWith('review-guesser:')) toDelete.push(k);
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

export function hasAnyForDay(gameDate: string): boolean {
    try {
        const data = loadDay(gameDate);
        return Boolean(data && data.results && Object.keys(data.results).length > 0);
    } catch {
        return false;
    }
}

export function hasAny(): boolean {
    try {
        for (let i = 0; i < localStorage.length; i++) {
            const k = localStorage.key(i);
            if (k && k.startsWith('review-guesser:')) return true;
        }
        return false;
    } catch {
        return false;
    }
}


