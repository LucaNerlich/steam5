import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';

const cookieGet = vi.fn<(name: string) => {value: string} | undefined>();
vi.mock('next/headers', () => ({
    cookies: async () => ({get: cookieGet}),
}));

import {postCommentAction} from './actions';

const BACKEND = 'http://backend.test';

function form(gameDate: unknown, body: unknown): FormData {
    const fd = new FormData();
    if (gameDate !== undefined) fd.append('gameDate', String(gameDate));
    if (body !== undefined) fd.append('body', String(body));
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

describe('postCommentAction input validation', () => {
    it('rejects a missing or invalid gameDate', async () => {
        expect(await postCommentAction(undefined, form(undefined, 'hi'))).toEqual({
            ok: false,
            error: 'Invalid date',
        });
        expect(await postCommentAction(undefined, form('not-a-date', 'hi'))).toEqual({
            ok: false,
            error: 'Invalid date',
        });
        expect(fetchMock).not.toHaveBeenCalled();
    });

    it('rejects a blank body', async () => {
        expect(await postCommentAction(undefined, form('2026-07-31', '   '))).toEqual({
            ok: false,
            error: 'Comment cannot be empty',
        });
        expect(fetchMock).not.toHaveBeenCalled();
    });

    it('rejects a body over the max length', async () => {
        const res = await postCommentAction(undefined, form('2026-07-31', 'x'.repeat(1001)));
        expect(res.ok).toBe(false);
        expect(res.error).toMatch(/1000 characters/);
        expect(fetchMock).not.toHaveBeenCalled();
    });
});

describe('postCommentAction authentication', () => {
    it('returns unauthorized when no session cookie is present', async () => {
        cookieGet.mockReturnValue(undefined);

        const res = await postCommentAction(undefined, form('2026-07-31', 'hello'));

        expect(res).toEqual({
            ok: false,
            unauthorized: true,
            error: 'Sign in with Steam to post a comment.',
        });
        expect(fetchMock).not.toHaveBeenCalled();
    });

    it('flags a 401 from the backend as unauthorized', async () => {
        cookieGet.mockReturnValue({value: 'jwt-token'});
        fetchMock.mockResolvedValue(mockResponse(401, {error: 'unauthenticated'}));

        const res = await postCommentAction(undefined, form('2026-07-31', 'hello'));

        expect(res.unauthorized).toBe(true);
        expect(res.ok).toBe(false);
    });
});

describe('postCommentAction happy path and upstream errors', () => {
    beforeEach(() => cookieGet.mockReturnValue({value: 'jwt-token'}));

    it('POSTs to the backend with Bearer auth and returns the comment id', async () => {
        fetchMock.mockResolvedValue(mockResponse(201, {id: 42, body: 'hello'}));

        const res = await postCommentAction(undefined, form('2026-07-31', '  hello  '));

        expect(res).toEqual({ok: true, commentId: 42});
        const [url, init] = fetchMock.mock.calls[0];
        expect(url).toBe(`${BACKEND}/api/review-game/comments/2026-07-31`);
        expect(init.method).toBe('POST');
        expect((init.headers as Record<string, string>).authorization).toBe('Bearer jwt-token');
        expect(JSON.parse(init.body as string)).toEqual({body: 'hello'});
    });

    it('returns a generic error when fetch throws', async () => {
        const consoleError = vi.spyOn(console, 'error').mockImplementation(() => undefined);
        fetchMock.mockRejectedValue(new Error('network down'));

        const res = await postCommentAction(undefined, form('2026-07-31', 'hello'));

        expect(res).toEqual({ok: false, error: 'Something went wrong. Please try again.'});
        expect(consoleError).toHaveBeenCalled();
        consoleError.mockRestore();
    });

    it('maps day_not_complete to a friendly error', async () => {
        fetchMock.mockResolvedValue(mockResponse(400, {error: 'day_not_complete'}));

        const res = await postCommentAction(undefined, form('2026-07-31', 'hello'));

        expect(res).toEqual({
            ok: false,
            error: 'Finish all rounds for this day before commenting.',
        });
    });

    it('maps rate_limit_exceeded to a friendly error', async () => {
        fetchMock.mockResolvedValue(mockResponse(429, {error: 'rate_limit_exceeded'}));

        const res = await postCommentAction(undefined, form('2026-07-31', 'hello'));

        expect(res).toEqual({
            ok: false,
            error: 'Too many comments — try again in a minute.',
        });
    });
});
