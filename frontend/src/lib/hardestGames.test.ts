import {describe, expect, it} from 'vitest';
import {formatDeception, formatMostMissed, HardestGame} from './hardestGames';

function makeGame(overrides: Partial<HardestGame> = {}): HardestGame {
    return {
        appId: 1,
        appName: 'Test Game',
        avgScore: 2.5,
        playerCount: 10,
        deceptionRate: 0,
        deceptionDirection: 'none',
        mostCommonWrongBucket: null,
        mostCommonWrongBucketCount: null,
        actualBucket: '1k-10k',
        latestPickDate: '2024-01-01',
        ...overrides,
    };
}

describe('formatDeception', () => {
    it('returns an em dash for neutral direction', () => {
        expect(formatDeception(makeGame({deceptionDirection: 'none', deceptionRate: 0.5}))).toBe('—');
    });

    it('formats over-guessed with an up-arrow and rounded percentage', () => {
        expect(formatDeception(makeGame({deceptionDirection: 'over', deceptionRate: 0.734}))).toBe('🔺 over-guessed 73%');
    });

    it('formats under-guessed with a down-arrow and rounded percentage', () => {
        expect(formatDeception(makeGame({deceptionDirection: 'under', deceptionRate: 0.128}))).toBe('🔻 under-guessed 13%');
    });

    it('rounds the percentage to the nearest integer', () => {
        expect(formatDeception(makeGame({deceptionDirection: 'over', deceptionRate: 0.5}))).toBe('🔺 over-guessed 50%');
        expect(formatDeception(makeGame({deceptionDirection: 'under', deceptionRate: 0.005}))).toBe('🔻 under-guessed 1%');
    });
});

describe('formatMostMissed', () => {
    it('renders bucket, count, and actual answer when data is present', () => {
        const game = makeGame({
            mostCommonWrongBucket: '10k-100k',
            mostCommonWrongBucketCount: 4,
            actualBucket: '1k-10k',
        });
        expect(formatMostMissed(game)).toBe('10k-100k (4×) → ✓ 1k-10k');
    });

    it('falls back to actual bucket only when no wrong guesses are recorded', () => {
        expect(formatMostMissed(makeGame({mostCommonWrongBucket: null, actualBucket: '1k-10k'}))).toBe('— → ✓ 1k-10k');
    });

    it('returns a plain em dash when there is no actual bucket and no wrong data', () => {
        expect(formatMostMissed(makeGame({mostCommonWrongBucket: null, actualBucket: ''}))).toBe('—');
    });

    it('treats a null count as missing data even if a bucket is present', () => {
        const game = makeGame({
            mostCommonWrongBucket: '10k-100k',
            mostCommonWrongBucketCount: null,
            actualBucket: '1k-10k',
        });
        expect(formatMostMissed(game)).toBe('— → ✓ 1k-10k');
    });
});
