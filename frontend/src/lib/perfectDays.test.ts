import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {fetchPerfectDays, PerfectDay} from './perfectDays';

vi.mock('@/lib/backend', () => ({BACKEND_ORIGIN: 'http://localhost:8080'}));

function mockResponse(ok: boolean, body: unknown, headers: Record<string, string> = {}) {
    return {
        ok,
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

describe('fetchPerfectDays', () => {
    it('fetches the perfect-days endpoint with JSON accept header and ISR revalidation tags', async () => {
        fetchMock.mockResolvedValue(mockResponse(true, []));

        await fetchPerfectDays();

        expect(fetchMock).toHaveBeenCalledTimes(1);
        const [url, init] = fetchMock.mock.calls[0];
        expect(url).toBe('http://localhost:8080/api/stats/game/perfect-days');
        expect(init.headers).toEqual({accept: 'application/json'});
        expect(init.next).toEqual({revalidate: 3600, tags: ['stats-perfect-days']});
    });

    it('returns the parsed data alongside the refreshed-at header on success', async () => {
        const data: PerfectDay[] = [{
            steamId: '76561198000000001',
            personaName: 'Alice',
            avatar: 'https://avatar/full.jpg',
            avatarBlurdata: 'data:blur',
            profileUrl: 'https://steamcommunity.com/id/alice',
            gameDate: '2026-01-15',
            appNames: ['Half-Life', 'Portal 2'],
        }];
        fetchMock.mockResolvedValue(mockResponse(true, data, {'X-Leaderboard-Refreshed-At': '2026-07-24T00:50:00Z'}));

        const result = await fetchPerfectDays();

        expect(result).toEqual({data, refreshedAt: '2026-07-24T00:50:00Z'});
    });

    it('defaults refreshedAt to null when the header is absent', async () => {
        fetchMock.mockResolvedValue(mockResponse(true, []));

        const result = await fetchPerfectDays();

        expect(result).toEqual({data: [], refreshedAt: null});
    });

    it('returns null when the backend responds with a non-OK status', async () => {
        fetchMock.mockResolvedValue(mockResponse(false, {error: 'boom'}));

        const result = await fetchPerfectDays();

        expect(result).toBeNull();
    });

    it('returns null when fetch throws, e.g. backend unreachable at build time', async () => {
        fetchMock.mockRejectedValue(new Error('network error'));

        const result = await fetchPerfectDays();

        expect(result).toBeNull();
    });

    it('returns null when the response body fails to parse as JSON', async () => {
        fetchMock.mockResolvedValue({
            ok: true,
            json: async () => {
                throw new Error('invalid json');
            },
            headers: {get: () => null},
        } as unknown as Response);

        const result = await fetchPerfectDays();

        expect(result).toBeNull();
    });
});