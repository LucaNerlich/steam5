import {describe, expect, it} from 'vitest';
import {
    currentUtcDate,
    HEAL_RETRY_DELAY_MS,
    isRoundStale,
    MAX_HEAL_ATTEMPTS,
    nextHealStep,
    type HealAttempt,
} from './roundFreshness';

describe('currentUtcDate', () => {
    it('formats the given instant as a UTC YYYY-MM-DD string', () => {
        // 23:30 UTC is still the same UTC day even though local zones may differ.
        expect(currentUtcDate(new Date('2026-06-16T23:30:00Z'))).toBe('2026-06-16');
        expect(currentUtcDate(new Date('2026-06-16T00:00:00Z'))).toBe('2026-06-16');
    });

    it('uses the UTC date, not the local date', () => {
        // 2026-06-16T23:30-05:00 is 2026-06-17T04:30Z — the UTC day has rolled.
        expect(currentUtcDate(new Date('2026-06-16T23:30:00-05:00'))).toBe('2026-06-17');
    });
});

describe('isRoundStale', () => {
    const today = '2026-06-16';

    it('is true when the rendered round predates today (the rollover bug)', () => {
        expect(isRoundStale('2026-06-15', today)).toBe(true);
        expect(isRoundStale('2026-01-01', today)).toBe(true);
    });

    it('is false for today\'s round', () => {
        expect(isRoundStale('2026-06-16', today)).toBe(false);
    });

    it('is false for a future date (never treat ahead-of-today as stale)', () => {
        expect(isRoundStale('2026-06-17', today)).toBe(false);
    });

    it('is false when gameDate is missing', () => {
        expect(isRoundStale(undefined, today)).toBe(false);
        expect(isRoundStale('', today)).toBe(false);
    });
});

describe('nextHealStep', () => {
    const date = '2026-06-15';

    it('fires immediately on the first attempt for a stale date', () => {
        const step = nextHealStep(date, null);
        expect(step.shouldHeal).toBe(true);
        expect(step.delayMs).toBe(0);
        expect(step.exhausted).toBe(false);
        expect(step.nextAttempt).toEqual({date, attempts: 1});
    });

    it('delays the second attempt for the same date', () => {
        const step = nextHealStep(date, {date, attempts: 1});
        expect(step.shouldHeal).toBe(true);
        expect(step.delayMs).toBe(HEAL_RETRY_DELAY_MS);
        expect(step.nextAttempt).toEqual({date, attempts: 2});
    });

    it('stops and reports exhausted once the cap is reached', () => {
        const prev: HealAttempt = {date, attempts: MAX_HEAL_ATTEMPTS};
        const step = nextHealStep(date, prev);
        expect(step.shouldHeal).toBe(false);
        expect(step.exhausted).toBe(true);
        // The attempt record is preserved so the cap stays enforced.
        expect(step.nextAttempt).toEqual(prev);
    });

    it('resets the attempt count when the stale date changes', () => {
        // A prior day exhausted its attempts; a newly-observed stale date starts fresh.
        const step = nextHealStep('2026-06-16', {date: '2026-06-15', attempts: MAX_HEAL_ATTEMPTS});
        expect(step.shouldHeal).toBe(true);
        expect(step.delayMs).toBe(0);
        expect(step.nextAttempt).toEqual({date: '2026-06-16', attempts: 1});
    });
});
