/**
 * Pure helpers for detecting a stale (pre-rollover) round and pacing the
 * self-heal retries. Extracted from ReviewGuesserRound so the rules can be
 * unit-tested without mounting the component (same rationale as authGuard.ts).
 *
 * The round page is force-static with `revalidate = 600`, so after the nightly
 * rollover (00:00 UTC) Next can keep serving the previous day's cached round.
 * The client detects this, busts the `round-today` cache, and re-renders.
 */

/** Today's date in UTC as YYYY-MM-DD. */
export function currentUtcDate(now: Date = new Date()): string {
    return now.toISOString().slice(0, 10);
}

/**
 * True when a rendered gameDate is older than today's UTC date — meaning the
 * statically-cached round predates the nightly rollover. A guess against such a
 * round would 400 (the backend has advanced to today's picks). Only strictly
 * older dates are stale; a same-day or (unexpected) future date is never stale.
 */
export function isRoundStale(gameDate: string | undefined, today: string = currentUtcDate()): boolean {
    return Boolean(gameDate) && gameDate! < today;
}

/** Maximum automatic self-heal attempts per stale gameDate before giving up. */
export const MAX_HEAL_ATTEMPTS = 2;

/** Delay before a retry attempt (the first attempt fires immediately). */
export const HEAL_RETRY_DELAY_MS = 5000;

export type HealAttempt = {date: string; attempts: number} | null;

export type HealStep = {
    /** Whether to fire a revalidate + refresh now. */
    shouldHeal: boolean;
    /** How long to wait before firing. */
    delayMs: number;
    /** The attempt record to persist for the next run. */
    nextAttempt: HealAttempt;
    /** True once retries are exhausted — surface a manual refresh instead. */
    exhausted: boolean;
};

/**
 * Decide the next self-heal step for a stale gameDate. Attempts are tracked per
 * date so the 00:00–00:01 UTC window — where the backend still serves yesterday
 * until its job runs and a refresh returns the same stale date — cannot spin in
 * a loop. The first attempt is immediate; subsequent ones are delayed.
 */
export function nextHealStep(gameDate: string, prev: HealAttempt): HealStep {
    const attempts = prev && prev.date === gameDate ? prev.attempts : 0;
    if (attempts >= MAX_HEAL_ATTEMPTS) {
        return {shouldHeal: false, delayMs: 0, nextAttempt: prev, exhausted: true};
    }
    return {
        shouldHeal: true,
        delayMs: attempts === 0 ? 0 : HEAL_RETRY_DELAY_MS,
        nextAttempt: {date: gameDate, attempts: attempts + 1},
        exhausted: false,
    };
}
