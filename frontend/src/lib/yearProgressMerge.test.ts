import {describe, expect, it} from 'vitest';
import {mergeYearProgress} from './yearProgressMerge';
import type {YearRoundProgress} from './yearStorage';

describe('mergeYearProgress', () => {
    it('keeps revealed hints from current state when local storage is empty', () => {
        const current: YearRoundProgress = {
            appId: 42,
            hintsUsed: 3,
            revealedHints: [
                {level: 1, content: 'Era hint'},
                {level: 2, content: 'Range hint'},
                {level: 3, content: 'Date hint'},
            ],
            unlockableHintLevels: [],
            completed: true,
            actualYear: 2020,
            lastGuessYear: 2020,
            points: 2,
        };

        const merged = mergeYearProgress(undefined, {
            roundIndex: 1,
            appId: 42,
            guessedYear: 2020,
            actualYear: 2020,
            hintsUsed: 0,
            bestDistance: 0,
            unlockableHintLevels: [],
            completed: true,
            points: 5,
        }, 5, current);

        expect(merged?.hintsUsed).toBe(3);
        expect(merged?.revealedHints).toHaveLength(3);
        expect(merged?.points).toBe(2);
    });

    it('never downgrades hints when server lags behind revealed cards', () => {
        const local: YearRoundProgress = {
            appId: 42,
            hintsUsed: 2,
            revealedHints: [
                {level: 1, content: 'Era hint'},
                {level: 2, content: 'Range hint'},
            ],
            unlockableHintLevels: [3],
            completed: false,
        };

        const merged = mergeYearProgress(local, {
            roundIndex: 1,
            appId: 42,
            guessedYear: 2010,
            actualYear: null,
            hintsUsed: 0,
            bestDistance: 10,
            unlockableHintLevels: [1],
            completed: false,
            points: 0,
        }, 5);

        expect(merged?.hintsUsed).toBe(2);
        expect(merged?.revealedHints).toHaveLength(2);
    });
});
