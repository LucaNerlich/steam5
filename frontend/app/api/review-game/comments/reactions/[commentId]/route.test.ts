import {afterEach, describe, expect, it, vi} from 'vitest';
import {NextRequest, NextResponse} from 'next/server';

const cookieGet = vi.fn(() => ({value: 'jwt-token'}));
vi.mock('next/headers', () => ({
    cookies: async () => ({get: cookieGet}),
}));

import {POST} from './route';

const params = Promise.resolve({commentId: '42'});

function request(body: string, origin = 'https://steam5.org', contentType = 'application/json') {
    return new NextRequest('https://steam5.org/api/review-game/comments/reactions/42', {
        method: 'POST',
        headers: {origin, 'content-type': contentType},
        body,
    });
}

afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
    cookieGet.mockReturnValue({value: 'jwt-token'});
});

describe('POST', () => {
    it('answers 400 for a malformed body instead of 502', async () => {
        // Parsing the untrusted body inside the gateway try made a client mistake look like a
        // bad gateway, so the client showed a server-failure message and 502 counters saw
        // phantom backend errors.
        const fetchMock = vi.fn();
        vi.stubGlobal('fetch', fetchMock);

        const res = await POST(request('nope', 'https://steam5.org', 'text/plain'), {params});

        expect(res.status).toBe(400);
        await expect(res.json()).resolves.toEqual({error: 'bad_request'});
        expect(fetchMock).not.toHaveBeenCalled();
    });

    it('passes a valid body and the upstream status through', async () => {
        const fetchMock = vi.fn(async (_url: string | URL | Request, _init?: RequestInit) =>
            NextResponse.json({like: 1}, {status: 200}));
        vi.stubGlobal('fetch', fetchMock);

        const res = await POST(request(JSON.stringify({reaction: 'like'})), {params});

        expect(res.status).toBe(200);
        await expect(res.json()).resolves.toEqual({like: 1});
        const init = fetchMock.mock.calls[0][1] as RequestInit;
        expect(init.method).toBe('POST');
        expect(init.body).toBe(JSON.stringify({reaction: 'like'}));
    });

    it('still rejects an untrusted origin before touching the body', async () => {
        const fetchMock = vi.fn();
        vi.stubGlobal('fetch', fetchMock);

        const res = await POST(request(JSON.stringify({reaction: 'like'}), 'https://evil.example'), {params});

        expect(res.status).toBe(403);
        expect(fetchMock).not.toHaveBeenCalled();
    });
});
