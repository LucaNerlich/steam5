import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {fetchRefreshedJson, formatRefreshedAt} from './leaderboard';

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

function mockResponse(ok: boolean, body: unknown, headers: Record<string, string> = {}) {
    return {
        ok,
        status: ok ? 200 : 500,
        json: async () => body,
        headers: {
            get: (name: string) => headers[name] ?? null,
        },
    } as unknown as Response;
}

let fetchMock: ReturnType<typeof vi.fn>;

beforeEach(() => {
    fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
});

afterEach(() => {
    vi.unstubAllGlobals();
});

describe('fetchRefreshedJson', () => {
    it('returns the parsed data with refreshedAt taken from the X-Leaderboard-Refreshed-At header', async () => {
        fetchMock.mockResolvedValue(mockResponse(true, [1, 2, 3], {'X-Leaderboard-Refreshed-At': '2026-07-24T00:50:00Z'}));

        const result = await fetchRefreshedJson<unknown[]>('/api/stats/game/perfect-days');

        expect(result).toEqual({data: [1, 2, 3], refreshedAt: '2026-07-24T00:50:00Z'});
    });

    it('returns refreshedAt null when the header is absent', async () => {
        fetchMock.mockResolvedValue(mockResponse(true, [1, 2, 3]));

        const result = await fetchRefreshedJson<unknown[]>('/api/stats/game/perfect-days');

        expect(result.refreshedAt).toBeNull();
    });

    it('rejects with the routed status on a non-ok response so SWR keeps the previous data', async () => {
        fetchMock.mockResolvedValue(mockResponse(false, {error: 'boom'}));

        const error = await fetchRefreshedJson('/x').then(
            () => null,
            (err: unknown) => err,
        );

        expect(error).toBeInstanceOf(Error);
        expect((error as Error).message).toBe('Failed to load /x: 500');
    });
});
