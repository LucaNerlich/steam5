import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {isActionStateForPick} from '@/lib/guessResolution';

// cookies() is read by getAuth(). Keep a hostile headers() mock available to
// prove the action never consults or relays caller-controlled forwarding data.
const cookieGet = vi.fn<(name: string) => {value: string} | undefined>();
const headerGet = vi.fn<(name: string) => string | null>();
vi.mock('next/headers', () => ({
    cookies: async () => ({get: cookieGet}),
    headers: async () => ({get: headerGet}),
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
    headerGet.mockReset();
    headerGet.mockReturnValue(null);
    fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
});

afterEach(() => {
    vi.unstubAllGlobals();
    vi.unstubAllEnvs();
});

/** Resolves the today-buckets fetch and the guess submission fetch in order. */
function stubCalls(...guessResponses: Response[]) {
    fetchMock.mockResolvedValueOnce(mockResponse(200, todayBody));
    for (const r of guessResponses) fetchMock.mockResolvedValueOnce(r);
}

describe('submitGuessAction backend origin', () => {
    it('fails closed on a plain-http remote backend in production', async () => {
        vi.stubEnv('NODE_ENV', 'production');
        process.env.NEXT_PUBLIC_API_DOMAIN = 'http://evil.example:8080';
        cookieGet.mockReturnValue({value: 'token'});

        const errorSpy = vi.spyOn(console, 'error').mockImplementation(() => undefined);

        const res = await submitGuessAction(undefined, form(10, 'Positive'));

        expect(res).toEqual({ok: false, appId: 10, error: 'Could not reach the game service — please try again.'});
        expect(fetchMock).not.toHaveBeenCalled();
        expect(errorSpy).toHaveBeenCalled();
    });

    it('proceeds for an https backend in production', async () => {
        vi.stubEnv('NODE_ENV', 'production');
        process.env.NEXT_PUBLIC_API_DOMAIN = 'https://api.example.com';
        cookieGet.mockReturnValue(undefined);
        fetchMock.mockResolvedValueOnce(mockResponse(200, todayBody));
        fetchMock.mockResolvedValueOnce(mockResponse(200, okBody));

        await submitGuessAction(undefined, form(10, 'Positive'));

        expect(fetchMock.mock.calls[0][0]).toBe('https://api.example.com/api/review-game/today');
    });
});

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

    it.each([
        ['a prefixed string', '10abc'],
        ['a decimal', '1.5'],
        ['an exponent form', '1e2'],
        ['leading/trailing whitespace', ' 10'],
        ['an unsafe integer', '99999999999999999999'],
    ])('rejects %s as appId', async (_label, raw) => {
        const res = await submitGuessAction(undefined, form(raw, 'Positive'));
        expect(res).toEqual({ok: false, error: 'Invalid input'});
        expect(fetchMock).not.toHaveBeenCalled();
    });

    it('rejects a missing bucketGuess', async () => {
        const res = await submitGuessAction(undefined, form(10, undefined));
        expect(res).toEqual({ok: false, appId: 10, error: 'Invalid input'});
        expect(fetchMock).not.toHaveBeenCalled();
    });

    it('rejects an oversized bucketGuess', async () => {
        const res = await submitGuessAction(undefined, form(10, 'x'.repeat(51)));
        expect(res).toEqual({ok: false, appId: 10, error: 'Invalid input'});
        expect(fetchMock).not.toHaveBeenCalled();
    });

    it('rejects a bucketGuess that is not one of today’s bucket labels', async () => {
        stubCalls();

        const res = await submitGuessAction(undefined, form(10, '0+'));

        expect(res).toEqual({ok: false, appId: 10, error: 'Invalid input'});
        expect(fetchMock).toHaveBeenCalledTimes(1);
    });

    it('fails closed when today’s buckets cannot be loaded', async () => {
        fetchMock.mockResolvedValueOnce(mockResponse(500, {}));

        const res = await submitGuessAction(undefined, form(10, 'Positive'));

        expect(res).toEqual({ok: false, appId: 10, error: 'Could not load today’s game — please try again'});
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
        expect(res).toEqual({ok: true, appId: 10, response: okBody, persisted: true});
    });

    it('flags a 401 as unauthorized (cookie present but invalid — the silent-logout case)', async () => {
        stubCalls(mockResponse(401, {error: 'nope'}));

        const res = await submitGuessAction(undefined, form(10, 'Positive'));

        expect(res).toEqual({ok: false, appId: 10, unauthorized: true, error: 'unauthorized'});
    });

    it('returns a generic upstream error for non-401 failures', async () => {
        stubCalls(mockResponse(500, {}));

        const res = await submitGuessAction(undefined, form(10, 'Positive'));

        expect(res).toEqual({ok: false, appId: 10, error: 'Upstream error 500'});
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
        expect(res).toEqual({ok: true, appId: 10, response: okBody, persisted: false});
    });

    it('does not flag anonymous failures as unauthorized', async () => {
        stubCalls(mockResponse(500, {}));

        const res = await submitGuessAction(undefined, form(10, 'Positive'));

        expect(res).toEqual({ok: false, appId: 10, error: 'Upstream error 500'});
    });

    it('does not relay a caller-controlled x-forwarded-for value', async () => {
        headerGet.mockImplementation((name: string) => (name === 'x-forwarded-for' ? '203.0.113.7' : null));
        stubCalls(mockResponse(200, okBody));

        await submitGuessAction(undefined, form(10, 'Positive'));

        const [, init] = fetchMock.mock.calls[1];
        expect((init.headers as Record<string, string>)['x-forwarded-for']).toBeUndefined();
        expect(headerGet).not.toHaveBeenCalled();
    });

    it('omits x-forwarded-for entirely when the inbound request has none', async () => {
        stubCalls(mockResponse(200, okBody));

        await submitGuessAction(undefined, form(10, 'Positive'));

        const [, init] = fetchMock.mock.calls[1];
        expect((init.headers as Record<string, string>)['x-forwarded-for']).toBeUndefined();
    });

    it('does not leak fetch error details to the client', async () => {
        const consoleError = vi.spyOn(console, 'error').mockImplementation(() => undefined);
        // Buckets load fine; the guess submission itself fails at the network level.
        fetchMock.mockResolvedValueOnce(mockResponse(200, todayBody));
        fetchMock.mockRejectedValue(new Error('connect ECONNREFUSED 127.0.0.1:8080'));

        const res = await submitGuessAction(undefined, form(10, 'Positive'));

        expect(res).toEqual({ok: false, appId: 10, error: 'Could not reach the game server — try again'});
        expect(JSON.stringify(res)).not.toContain('127.0.0.1');
        consoleError.mockRestore();
    });
});

describe('submitGuessAction appId stamp (round scoping)', () => {
    beforeEach(() => cookieGet.mockReturnValue(undefined));

    it('stamps every failure branch with the requested appId', async () => {
        stubCalls(mockResponse(500, {}));
        expect(await submitGuessAction(undefined, form(10, 'Positive')))
            .toMatchObject({ok: false, appId: 10});

        fetchMock.mockReset();
        stubCalls(mockResponse(401, {error: 'nope'}));
        cookieGet.mockReturnValue({value: 'jwt-token'});
        expect(await submitGuessAction(undefined, form(10, 'Positive')))
            .toMatchObject({ok: false, appId: 10, unauthorized: true});

        fetchMock.mockReset();
        fetchMock.mockResolvedValueOnce(mockResponse(500, {}));
        expect(await submitGuessAction(undefined, form(10, 'Positive')))
            .toMatchObject({ok: false, appId: 10});

        cookieGet.mockReturnValue(undefined);
        const consoleError = vi.spyOn(console, 'error').mockImplementation(() => undefined);
        fetchMock.mockReset();
        fetchMock.mockResolvedValueOnce(mockResponse(200, todayBody));
        fetchMock.mockRejectedValue(new Error('connect ECONNREFUSED'));
        expect(await submitGuessAction(undefined, form(10, 'Positive')))
            .toMatchObject({ok: false, appId: 10});
        consoleError.mockRestore();
    });

    it('does not stamp a branch whose requested appId never parsed', async () => {
        const res = await submitGuessAction(undefined, form('10abc', 'Positive'));
        expect(res.appId).toBeUndefined();
    });

    it('keeps a failed state for round N from being attributed to round N+1', async () => {
        // The round page reuses its component: after the player moves on, the retained
        // state must fail the pick gate, or the previous round's banner would render here.
        stubCalls(mockResponse(500, {}));

        const res = await submitGuessAction(undefined, form(10, 'Positive'));

        expect(res).toMatchObject({ok: false, appId: 10, error: 'Upstream error 500'});
        expect(isActionStateForPick(res, 11)).toBe(false);
        expect(isActionStateForPick(res, 10)).toBe(true);
    });
});
