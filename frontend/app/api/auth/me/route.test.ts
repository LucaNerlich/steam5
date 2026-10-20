import {afterEach, describe, expect, it, vi} from 'vitest';
import {NextRequest} from 'next/server';

vi.mock('next/headers', () => ({
    cookies: async () => ({get: () => ({value: 'token-1'})}),
}));

const req = () => new NextRequest('https://steam5.org/api/auth/me');

afterEach(() => {
    vi.unstubAllEnvs();
    vi.unstubAllGlobals();
    vi.resetModules();
});

describe('GET /api/auth/me bearer egress', () => {
    it('fails closed without fetching when the backend origin is plain-http in production', async () => {
        vi.resetModules();
        vi.stubEnv('NODE_ENV', 'production');
        vi.stubEnv('NEXT_PUBLIC_API_DOMAIN', 'http://backend:8080');
        const fetchMock = vi.fn();
        vi.stubGlobal('fetch', fetchMock);
        const {GET} = await import('./route');

        const res = await GET(req());

        expect(res.status).toBe(200);
        await expect(res.json()).resolves.toEqual({signedIn: false});
        expect(fetchMock).not.toHaveBeenCalled();
    });

    it('sends the bearer token to a trusted https backend', async () => {
        vi.resetModules();
        vi.stubEnv('NODE_ENV', 'production');
        vi.stubEnv('NEXT_PUBLIC_API_DOMAIN', 'https://api.example.com');
        const fetchMock = vi.fn(async () => new Response(
            JSON.stringify({valid: true, steamId: '765'}),
            {status: 200, headers: {'content-type': 'application/json'}},
        ));
        vi.stubGlobal('fetch', fetchMock);
        const {GET} = await import('./route');

        const res = await GET(req());

        expect(res.status).toBe(200);
        const init = fetchMock.mock.calls[0]?.[1] as RequestInit;
        expect((init.headers as Record<string, string>).authorization).toBe('Bearer token-1');
    });
});
