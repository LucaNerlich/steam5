import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

const cookieGet = vi.fn<(name: string) => {value: string} | undefined>();
vi.mock('next/headers', () => ({
    cookies: async () => ({get: cookieGet}),
}));

import {submitYearGuessAction} from './actions';

const BACKEND = 'http://backend.test';

function form(appId: unknown, guessYear: unknown): FormData {
    const fd = new FormData();
    if (appId !== undefined) fd.append('appId', String(appId));
    if (guessYear !== undefined) fd.append('guessYear', String(guessYear));
    return fd;
}

function mockResponse(status: number, body: unknown) {
    return {
        ok: status >= 200 && status < 300,
        status,
        json: async () => body,
    } as Response;
}

let fetchMock: ReturnType<typeof vi.fn>;

beforeEach(() => {
    process.env.NEXT_PUBLIC_API_DOMAIN = BACKEND;
    cookieGet.mockReset();
    fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
});

afterEach(() => {
    vi.unstubAllGlobals();
});

describe('submitYearGuessAction', () => {
    it('rejects invalid year input', async () => {
        const result = await submitYearGuessAction(undefined, form(42, 'abc'));
        expect(result).toEqual({ok: false, error: 'Enter a valid year'});
        expect(fetchMock).not.toHaveBeenCalled();
    });

    it('submits anonymous guess to public endpoint', async () => {
        cookieGet.mockReturnValue(undefined);
        const body = {
            appId: 42,
            guessYear: 2017,
            correct: false,
            distance: 3,
            releaseYear: null,
            hintsUsed: 0,
            maxPoints: 5,
            unlockableHintLevels: [1],
            points: null,
        };
        fetchMock.mockResolvedValue(mockResponse(200, body));

        const result = await submitYearGuessAction(undefined, form(42, 2017));

        const [url] = fetchMock.mock.calls[0];
        expect(url).toBe(`${BACKEND}/api/year-game/guess`);
        expect(result).toEqual({ok: true, response: body, persisted: false});
    });
});
