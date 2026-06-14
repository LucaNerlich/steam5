import {describe, expect, it} from 'vitest';
import {scoreForRound, tierEmoji, tierForDistance} from './scoring';

describe('tierForDistance', () => {
    it('maps distances to tiers', () => {
        expect(tierForDistance(0)).toBe('exact');
        expect(tierForDistance(-1)).toBe('exact'); // <= 0 is exact
        expect(tierForDistance(1)).toBe('close');
        expect(tierForDistance(2)).toBe('near');
        expect(tierForDistance(3)).toBe('far');
        expect(tierForDistance(99)).toBe('far');
    });
});

describe('tierEmoji', () => {
    it('maps each tier to its colour block', () => {
        expect(tierEmoji('exact')).toBe('🟩');
        expect(tierEmoji('close')).toBe('🟨');
        expect(tierEmoji('near')).toBe('🟧');
        expect(tierEmoji('far')).toBe('🟥');
    });
});

describe('scoreForRound', () => {
    const buckets = ['0-20', '20-40', '40-60', '60-80', '80-100'];

    it('awards 5 points and a green bar for an exact match', () => {
        expect(scoreForRound(buckets, '40-60', '40-60')).toEqual({bar: '🟩', points: 5, distance: 0});
    });

    it('awards 3 points for a distance of 1', () => {
        expect(scoreForRound(buckets, '20-40', '40-60')).toEqual({bar: '🟨', points: 3, distance: 1});
    });

    it('awards 1 point for a distance of 2', () => {
        expect(scoreForRound(buckets, '0-20', '40-60')).toEqual({bar: '🟧', points: 1, distance: 2});
    });

    it('floors points at 0 for far guesses (never negative)', () => {
        expect(scoreForRound(buckets, '0-20', '80-100')).toEqual({bar: '🟥', points: 0, distance: 4});
    });

    it('returns a neutral zero score when a label is not in the bucket list', () => {
        expect(scoreForRound(buckets, 'nonsense', '40-60')).toEqual({bar: '⬜', points: 0, distance: 0});
        expect(scoreForRound(buckets, '40-60', 'nonsense')).toEqual({bar: '⬜', points: 0, distance: 0});
    });
});
