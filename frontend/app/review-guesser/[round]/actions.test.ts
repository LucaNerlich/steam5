import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

// cookies() is read inside the server action; mock it so we can control whether
// an s5_token cookie is present.
const cookieGet = vi.fn<(name: string) => {value: string} | undefined>();
vi.mock('next/headers', () => ({
    cookies: async () => ({get: cookieGet}),
}));

import {submitGuessAction} from './actions';

const BACKEND = 'http://backend.test';

function form(appId: unknown, bucketGuess: unknown): FormData {
    const fd = new FormData();
    if (appId !== undefined) fd.append('appId', String(appId));
    if (bucketGuess !== undefined) fd.append('bucketGuess', String(bucketGuess));
    return fd;
}

function mockResponse(status: number, body: unknown) {
    return {
        ok: status >= 200 && status < 300,
        status,
        json: async () => body,
    } as Response;
}

const okBody = {appId: 10, actualBucket: 'Positive', totalReviews: 100, correct: true};

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

describe('submitGuessAction input validation', () => {
    it('rejects a missing/invalid appId', async () => {
        const res = await submitGuessAction(undefined, form(undefined, 'Positive'));
        expect(res).toEqual({ok: false, error: 'Invalid input'});
        expect(fetchMock).not.toHaveBeenCalled();
    });

    it('rejects a missing bucketGuess', async () => {
        const res = await submitGuessAction(undefined, form(10, undefined));
        expect(res).toEqual({ok: false, error: 'Invalid input'});
        expect(fetchMock).not.toHaveBeenCalled();
    });
});

describe('submitGuessAction with a session cookie (authenticated)', () => {
    beforeEach(() => cookieGet.mockReturnValue({value: 'jwt-token'}));

    it('routes to the authenticated endpoint with a Bearer header and marks the guess persisted', async () => {
        fetchMock.mockResolvedValue(mockResponse(200, okBody));

        const res = await submitGuessAction(undefined, form(10, 'Positive'));

        const [url, init] = fetchMock.mock.calls[0];
        expect(url).toBe(`${BACKEND}/api/review-game/guess-auth`);
        expect((init.headers as Record<string, string>).authorization).toBe('Bearer jwt-token');
        expect(res).toEqual({ok: true, response: okBody, persisted: true});
    });

    it('flags a 401 as unauthorized (cookie present but invalid — the silent-logout case)', async () => {
        fetchMock.mockResolvedValue(mockResponse(401, {error: 'nope'}));

        const res = await submitGuessAction(undefined, form(10, 'Positive'));

        expect(res).toEqual({ok: false, unauthorized: true, error: 'unauthorized'});
    });

    it('returns a generic upstream error for non-401 failures', async () => {
        fetchMock.mockResolvedValue(mockResponse(500, {}));

        const res = await submitGuessAction(undefined, form(10, 'Positive'));

        expect(res).toEqual({ok: false, error: 'Upstream error 500'});
        expect(res.unauthorized).toBeUndefined();
    });
});

describe('submitGuessAction without a session cookie (anonymous)', () => {
    beforeEach(() => cookieGet.mockReturnValue(undefined));

    it('routes to the anonymous endpoint without an auth header and marks the guess not persisted', async () => {
        fetchMock.mockResolvedValue(mockResponse(200, okBody));

        const res = await submitGuessAction(undefined, form(10, 'Positive'));

        const [url, init] = fetchMock.mock.calls[0];
        expect(url).toBe(`${BACKEND}/api/review-game/guess`);
        expect((init.headers as Record<string, string>).authorization).toBeUndefined();
        expect(res).toEqual({ok: true, response: okBody, persisted: false});
    });

    it('does not flag anonymous failures as unauthorized', async () => {
        fetchMock.mockResolvedValue(mockResponse(500, {}));

        const res = await submitGuessAction(undefined, form(10, 'Positive'));

        expect(res).toEqual({ok: false, error: 'Upstream error 500'});
    });
});
