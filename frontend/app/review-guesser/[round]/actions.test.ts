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
const todayBody = {buckets: ['Positive', 'Negative']};

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

/** Resolves the today-buckets fetch and the guess submission fetch in order. */
function stubCalls(...guessResponses: Response[]) {
    fetchMock.mockResolvedValueOnce(mockResponse(200, todayBody));
    for (const r of guessResponses) fetchMock.mockResolvedValueOnce(r);
}

describe('submitGuessAction input validation', () => {
    it('rejects a missing/invalid appId', async () => {
        const res = await submitGuessAction(undefined, form(undefined, 'Positive'));
        expect(res).toEqual({ok: false, error: 'Invalid input'});
        expect(fetchMock).not.toHaveBeenCalled();
    });

    it('rejects a negative appId', async () => {
        const res = await submitGuessAction(undefined, form(-5, 'Positive'));
        expect(res).toEqual({ok: false, error: 'Invalid input'});
        expect(fetchMock).not.toHaveBeenCalled();
    });

    it('rejects a missing bucketGuess', async () => {
        const res = await submitGuessAction(undefined, form(10, undefined));
        expect(res).toEqual({ok: false, error: 'Invalid input'});
        expect(fetchMock).not.toHaveBeenCalled();
    });

    it('rejects an oversized bucketGuess', async () => {
        const res = await submitGuessAction(undefined, form(10, 'x'.repeat(51)));
        expect(res).toEqual({ok: false, error: 'Invalid input'});
        expect(fetchMock).not.toHaveBeenCalled();
    });

    it('rejects a bucketGuess that is not one of today’s bucket labels', async () => {
        stubCalls();

        const res = await submitGuessAction(undefined, form(10, '0+'));

        expect(res).toEqual({ok: false, error: 'Invalid input'});
        expect(fetchMock).toHaveBeenCalledTimes(1);
    });

    it('fails closed when today’s buckets cannot be loaded', async () => {
        fetchMock.mockResolvedValueOnce(mockResponse(500, {}));

        const res = await submitGuessAction(undefined, form(10, 'Positive'));

        expect(res).toEqual({ok: false, error: 'Could not load today’s game — please try again'});
        expect(fetchMock).toHaveBeenCalledTimes(1);
    });
});

describe('submitGuessAction with a session cookie (authenticated)', () => {
    beforeEach(() => cookieGet.mockReturnValue({value: 'jwt-token'}));

    it('routes to the authenticated endpoint with a Bearer header and marks the guess persisted', async () => {
        stubCalls(mockResponse(200, okBody));

        const res = await submitGuessAction(undefined, form(10, 'Positive'));

        const [url, init] = fetchMock.mock.calls[1];
        expect(url).toBe(`${BACKEND}/api/review-game/guess-auth`);
        expect((init.headers as Record<string, string>).authorization).toBe('Bearer jwt-token');
        expect(res).toEqual({ok: true, response: okBody, persisted: true});
    });

    it('flags a 401 as unauthorized (cookie present but invalid — the silent-logout case)', async () => {
        stubCalls(mockResponse(401, {error: 'nope'}));

        const res = await submitGuessAction(undefined, form(10, 'Positive'));

        expect(res).toEqual({ok: false, unauthorized: true, error: 'unauthorized'});
    });

    it('returns a generic upstream error for non-401 failures', async () => {
        stubCalls(mockResponse(500, {}));

        const res = await submitGuessAction(undefined, form(10, 'Positive'));

        expect(res).toEqual({ok: false, error: 'Upstream error 500'});
        expect(res.unauthorized).toBeUndefined();
    });
});

describe('submitGuessAction without a session cookie (anonymous)', () => {
    beforeEach(() => cookieGet.mockReturnValue(undefined));

    it('routes to the anonymous endpoint without an auth header and marks the guess not persisted', async () => {
        stubCalls(mockResponse(200, okBody));

        const res = await submitGuessAction(undefined, form(10, 'Positive'));

        const [url, init] = fetchMock.mock.calls[1];
        expect(url).toBe(`${BACKEND}/api/review-game/guess`);
        expect((init.headers as Record<string, string>).authorization).toBeUndefined();
        expect(res).toEqual({ok: true, response: okBody, persisted: false});
    });

    it('does not flag anonymous failures as unauthorized', async () => {
        stubCalls(mockResponse(500, {}));

        const res = await submitGuessAction(undefined, form(10, 'Positive'));

        expect(res).toEqual({ok: false, error: 'Upstream error 500'});
    });
});
