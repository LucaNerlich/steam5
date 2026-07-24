import {describe, expect, it} from 'vitest';
import {formatRefreshedAt} from './leaderboard';

describe('formatRefreshedAt', () => {
    it('formats a valid ISO timestamp into a non-empty localized string', () => {
        const result = formatRefreshedAt('2026-07-24T00:40:00Z');
        expect(result).not.toBeNull();
        expect(typeof result).toBe('string');
        expect(result!.length).toBeGreaterThan(0);
    });

    it('returns null for null input', () => {
        expect(formatRefreshedAt(null)).toBeNull();
    });

    it('returns null for undefined input', () => {
        expect(formatRefreshedAt(undefined)).toBeNull();
    });

    it('returns null for an empty string', () => {
        expect(formatRefreshedAt('')).toBeNull();
    });

    it('returns null for an unparseable string', () => {
        expect(formatRefreshedAt('not-a-date')).toBeNull();
    });
});
