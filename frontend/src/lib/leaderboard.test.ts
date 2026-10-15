import {describe, expect, it} from 'vitest';
import {formatRefreshedAt, parseServerOffsetHeader} from './leaderboard';

describe('parseServerOffsetHeader', () => {
    it('parses a signed numeric offset', () => {
        const headers = new Headers({'X-Server-Timezone-Offset': '-120'});
        expect(parseServerOffsetHeader(headers)).toBe(-120);
    });

    it('falls back to 0 when the header is absent', () => {
        expect(parseServerOffsetHeader(new Headers())).toBe(0);
    });

    it('falls back to 0 for a non-numeric header instead of yielding NaN', () => {
        const headers = new Headers({'X-Server-Timezone-Offset': 'not-a-number'});
        expect(parseServerOffsetHeader(headers)).toBe(0);
    });

    it('falls back to 0 for an empty header value', () => {
        const headers = new Headers({'X-Server-Timezone-Offset': ''});
        expect(parseServerOffsetHeader(headers)).toBe(0);
    });
});

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
