import {describe, expect, it} from 'vitest';
import {formatChallengeTime} from './NextChallengeTime';

/**
 * The footer line hides itself when there is nothing usable to show. `new Date("soon")` is an
 * Invalid Date rather than a throw, so the guard has to be on `getTime()` — an unguarded parse
 * rendered "NaN:NaN AM", which is truthy and therefore bypassed the caller's null check.
 *
 * The suite pins TZ=UTC (vitest.config.ts), so the expected values are deterministic.
 */
describe('formatChallengeTime', () => {
    it('returns null for a timestamp that cannot be parsed', () => {
        expect(formatChallengeTime('soon')).toBeNull();
        expect(formatChallengeTime('')).toBeNull();
        expect(formatChallengeTime(null)).toBeNull();
        expect(formatChallengeTime(undefined)).toBeNull();
    });

    it('formats a valid UTC timestamp in local 12-hour time', () => {
        expect(formatChallengeTime('2025-11-12T00:01:00Z')).toBe('12:01 AM');
        expect(formatChallengeTime('2025-11-12T13:05:00Z')).toBe('1:05 PM');
    });
});
