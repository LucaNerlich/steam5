import {afterEach, describe, expect, it, vi} from 'vitest';
import {NextRequest, NextResponse} from 'next/server';
import {forwardedForHeaderValue, proxyBackendJson} from './backend';

describe('forwardedForHeaderValue', () => {
    it('relays the client IP the trusted edge proxy set', () => {
        expect(forwardedForHeaderValue('203.0.113.7')).toEqual({'x-forwarded-for': '203.0.113.7'});
    });

    it('relays a multi-hop chain unchanged rather than picking one entry', () => {
        expect(forwardedForHeaderValue('203.0.113.7, 70.41.3.18')).toEqual({
            'x-forwarded-for': '203.0.113.7, 70.41.3.18',
        });
    });

    it('relays nothing when the edge did not set the header', () => {
        expect(forwardedForHeaderValue(null)).toEqual({});
        expect(forwardedForHeaderValue(undefined)).toEqual({});
        expect(forwardedForHeaderValue('')).toEqual({});
    });
});

describe('proxyBackendJson', () => {
    const req = (xff?: string) => new NextRequest('https://steam5.org/api/x', {
        headers: xff ? {'x-forwarded-for': xff} : {},
    });

    afterEach(() => {
        vi.unstubAllGlobals();
        vi.restoreAllMocks();
    });

    it('does not put the caller IP into a shared-cached upstream call', async () => {
        const fetchMock = vi.fn(async (_url: string | URL | Request, _init?: RequestInit) => NextResponse.json({ok: true}));
        vi.stubGlobal('fetch', fetchMock);

        await proxyBackendJson(req('203.0.113.7'), {
            path: '/api/leaderboard/all',
            revalidate: 600,
            errorMessage: 'nope',
        });

        const init = fetchMock.mock.calls[0][1] as RequestInit;
        expect(init.headers).toEqual({accept: 'application/json'});
        expect(init).not.toHaveProperty('signal', undefined);
        expect((init as {next?: {revalidate?: number}}).next?.revalidate).toBe(600);
    });

    it('relays the caller IP on a per-request route', async () => {
        const fetchMock = vi.fn(async (_url: string | URL | Request, _init?: RequestInit) => NextResponse.json({ok: true}));
        vi.stubGlobal('fetch', fetchMock);

        await proxyBackendJson(req('203.0.113.7'), {path: '/api/users/search?q=a', errorMessage: 'nope'});

        const init = fetchMock.mock.calls[0][1] as RequestInit;
        expect(init.headers).toEqual({accept: 'application/json', 'x-forwarded-for': '203.0.113.7'});
        expect((init as {next?: unknown}).next).toBeUndefined();
    });

    it('passes an upstream error through with its status and logs it once', async () => {
        const errorSpy = vi.spyOn(console, 'error').mockImplementation(() => undefined);
        vi.stubGlobal('fetch', vi.fn(async () => NextResponse.json({detail: 'boom'}, {status: 503})));

        const res = await proxyBackendJson(req(), {path: '/api/x', errorMessage: 'failed'});

        expect(res.status).toBe(503);
        await expect(res.json()).resolves.toEqual({detail: 'boom'});
        expect(errorSpy).toHaveBeenCalledTimes(1);
    });

    it('never applies the route Cache-Control to an error response', async () => {
        vi.spyOn(console, 'error').mockImplementation(() => undefined);
        vi.stubGlobal('fetch', vi.fn(async () => new NextResponse('nope', {status: 500})));

        const res = await proxyBackendJson(req(), {
            path: '/api/x',
            errorMessage: 'failed',
            cacheControl: 'public, s-maxage=60, max-age=30',
        });

        expect(res.status).toBe(500);
        expect(res.headers.get('cache-control')).toBeNull();
    });

    it('applies the route Cache-Control to a successful response', async () => {
        vi.stubGlobal('fetch', vi.fn(async () => NextResponse.json({ok: true})));

        const res = await proxyBackendJson(req(), {
            path: '/api/x',
            errorMessage: 'failed',
            cacheControl: 'public, s-maxage=60, max-age=30',
        });

        expect(res.status).toBe(200);
        expect(res.headers.get('cache-control')).toBe('public, s-maxage=60, max-age=30');
    });

    it('answers 502 when the upstream call exceeds its bound', async () => {
        vi.spyOn(console, 'error').mockImplementation(() => undefined);
        vi.stubGlobal('fetch', vi.fn((_url: string, init: RequestInit) => new Promise((_resolve, reject) => {
            init.signal?.addEventListener('abort', () => reject(new Error('aborted')));
        })));

        const res = await proxyBackendJson(req(), {path: '/api/x', errorMessage: 'failed', timeoutMs: 5});

        expect(res.status).toBe(502);
        await expect(res.json()).resolves.toEqual({error: 'failed'});
    });
});
