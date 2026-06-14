import {afterEach, beforeEach, describe, expect, it} from 'vitest';
import {clearAll, clearDay, hasAny, hasAnyForDay, loadDay, saveRound, type RoundResult} from './storage';

// Minimal in-memory Storage stand-in. storage.ts reads both window.localStorage
// and the bare localStorage global, so we point both at the same instance.
class MemStorage {
    private store = new Map<string, string>();
    get length() { return this.store.size; }
    key(i: number) { return Array.from(this.store.keys())[i] ?? null; }
    getItem(k: string) { return this.store.has(k) ? this.store.get(k)! : null; }
    setItem(k: string, v: string) { this.store.set(k, String(v)); }
    removeItem(k: string) { this.store.delete(k); }
    clear() { this.store.clear(); }
}

const result: RoundResult = {
    appId: 10,
    selectedLabel: 'A',
    actualBucket: 'A',
    totalReviews: 5,
    correct: true,
};

beforeEach(() => {
    const mem = new MemStorage();
    (globalThis as Record<string, unknown>).window = {localStorage: mem};
    (globalThis as Record<string, unknown>).localStorage = mem;
});

afterEach(() => {
    delete (globalThis as Record<string, unknown>).window;
    delete (globalThis as Record<string, unknown>).localStorage;
});

describe('saveRound / loadDay', () => {
    it('returns null for a date with nothing stored', () => {
        expect(loadDay('2024-01-01')).toBeNull();
    });

    it('persists a round and reads it back under its date key', () => {
        const day = saveRound('2024-01-01', 1, 5, result);
        expect(day).toEqual({totalRounds: 5, results: {1: result}});
        expect(loadDay('2024-01-01')).toEqual({totalRounds: 5, results: {1: result}});
    });

    it('merges multiple rounds into the same day', () => {
        saveRound('2024-01-01', 1, 5, result);
        const day = saveRound('2024-01-01', 2, 5, {...result, appId: 11});
        expect(Object.keys(day!.results)).toEqual(['1', '2']);
    });

    it('keeps days isolated by date', () => {
        saveRound('2024-01-01', 1, 5, result);
        expect(loadDay('2024-01-02')).toBeNull();
    });

    it('returns null from loadDay when stored JSON is corrupt', () => {
        (globalThis as { localStorage: Storage }).localStorage.setItem('review-guesser:2024-01-01', '{not json');
        expect(loadDay('2024-01-01')).toBeNull();
    });
});

describe('hasAnyForDay / hasAny', () => {
    it('reports presence per day and globally', () => {
        expect(hasAny()).toBe(false);
        expect(hasAnyForDay('2024-01-01')).toBe(false);
        saveRound('2024-01-01', 1, 5, result);
        expect(hasAnyForDay('2024-01-01')).toBe(true);
        expect(hasAnyForDay('2024-01-02')).toBe(false);
        expect(hasAny()).toBe(true);
    });
});

describe('clearDay / clearAll', () => {
    it('clearDay removes only the targeted date', () => {
        saveRound('2024-01-01', 1, 5, result);
        saveRound('2024-01-02', 1, 5, result);
        clearDay('2024-01-01');
        expect(loadDay('2024-01-01')).toBeNull();
        expect(loadDay('2024-01-02')).not.toBeNull();
    });

    it('clearAll removes every review-guesser entry but leaves unrelated keys', () => {
        saveRound('2024-01-01', 1, 5, result);
        saveRound('2024-01-02', 1, 5, result);
        (globalThis as { localStorage: Storage }).localStorage.setItem('theme', 'dark');
        clearAll();
        expect(hasAny()).toBe(false);
        expect((globalThis as { localStorage: Storage }).localStorage.getItem('theme')).toBe('dark');
    });
});
