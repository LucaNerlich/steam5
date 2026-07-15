export const YEAR_MAX_HINTS = 3;

/** Mirrors backend YearGuessEvaluator.maxPointsForHintsUsed. */
export function yearPointsForHintsUsed(maxPointsAtStart: number, hintsUsed: number): number {
    const capped = Math.max(0, Math.min(hintsUsed, YEAR_MAX_HINTS));
    return Math.max(0, maxPointsAtStart - capped);
}

/** Prefer explicit count, then revealed hint cards (client truth when server lags). */
export function effectiveYearHintsUsed(hintsUsed: number, revealedHintCount: number): number {
    return Math.max(hintsUsed, revealedHintCount);
}
