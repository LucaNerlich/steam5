/**
 * Parses a round path segment. The complete string must be a positive safe
 * integer — partial matches like "1abc", "1.5", or "1e2" are rejected so
 * malformed URLs consistently 404 instead of silently becoming round 1.
 */
export function parseRoundIndex(round: string | undefined): number | null {
    if (typeof round !== 'string' || !/^\d+$/.test(round)) return null;
    const n = Number(round);
    // Reject zero-padded spellings too: /^\d+$/ matches "01", which rendered round 1
    // under a second ISR key instead of 404ing like every other malformed URL.
    if (String(n) !== round) return null;
    return Number.isSafeInteger(n) && n >= 1 ? n : null;
}
