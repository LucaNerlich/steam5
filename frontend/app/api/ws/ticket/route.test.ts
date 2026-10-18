import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {NextRequest, NextResponse} from 'next/server';

vi.mock('next/headers', () => ({
    cookies: async () => ({get: () => ({value: 'jwt-token'})}),
}));

const request = () => new NextRequest('https://steam5.org/api/ws/ticket?scopeKey=2026-10-16:1:9', {
    method: 'POST',
});

/** The route and BACKEND_ORIGIN read their env at module scope, so each case loads a fresh copy. */
async function loadRoute() {
    vi.resetModules();
    return await import('./route');
}

beforeEach(() => {
    vi.stubEnv('NODE_ENV', 'production');
    vi.stubEnv('NEXT_PUBLIC_API_DOMAIN', 'http://backend:8080');
});

afterEach(() => {
    vi.unstubAllEnvs();
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
});

describe('POST ws ticket origin gate', () => {
    it('accepts an allow-list entry written with a trailing slash', async () => {
        // The raw-string comparison refused this spelling and answered {ticket: null} with 200,
        // so signed-in users silently connected anonymously while logout accepted the same pair.
        vi.stubEnv('ALLOWED_BACKEND_ORIGINS', 'http://backend:8080/');
        const {POST} = await loadRoute();
        const fetchMock = vi.fn(async () => NextResponse.json({ticket: 't'}));
        vi.stubGlobal('fetch', fetchMock);

        const res = await POST(request());

        await expect(res.json()).resolves.toEqual({ticket: 't'});
        expect(fetchMock).toHaveBeenCalledTimes(1);
    });

    it('refuses an untrusted plain-http backend with a null ticket', async () => {
        vi.stubEnv('ALLOWED_BACKEND_ORIGINS', 'https://other.example.com');
        const {POST} = await loadRoute();
        const fetchMock = vi.fn();
        vi.stubGlobal('fetch', fetchMock);
        vi.spyOn(console, 'error').mockImplementation(() => undefined);

        const res = await POST(request());

        await expect(res.json()).resolves.toEqual({ticket: null});
        expect(fetchMock).not.toHaveBeenCalled();
    });
});
