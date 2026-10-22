import {afterEach, describe, expect, it, vi} from 'vitest';
import {NextRequest} from 'next/server';

vi.mock('next/headers', () => ({
    cookies: async () => ({get: () => ({value: 'token-1'})}),
}));

const req = () => new NextRequest('https://steam5.org/api/auth/me');

async function loadRoute(backend: string, fetchMock: ReturnType<typeof vi.fn>) {
    vi.resetModules();
    vi.stubEnv('NODE_ENV', 'production');
    vi.stubEnv('NEXT_PUBLIC_API_DOMAIN', backend);
    vi.stubGlobal('fetch', fetchMock);
    return import('./route');
}

afterEach(() => {
    vi.unstubAllEnvs();
    vi.unstubAllGlobals();
    vi.resetModules();
    vi.restoreAllMocks();
});

describe('GET /api/auth/me bearer egress', () => {
    it('fails closed without fetching when the backend origin is plain-http in production', async () => {
        vi.spyOn(console, 'error').mockImplementation(() => undefined);
        const fetchMock = vi.fn();
        const {GET} = await loadRoute('http://backend:8080', fetchMock);

        const res = await GET(req());

        // A configuration failure is not proof the user is signed out.
        expect(res.status).toBe(503);
        await expect(res.json()).resolves.toEqual({error: 'backend origin not allowed'});
        expect(fetchMock).not.toHaveBeenCalled();
    });

    it('sends the bearer token to a trusted https backend', async () => {
        const fetchMock = vi.fn(async (_url: string | URL | Request, _init?: RequestInit) => new Response(
            JSON.stringify({valid: true, steamId: '765'}),
            {status: 200, headers: {'content-type': 'application/json'}},
        ));
        const {GET} = await loadRoute('https://api.example.com', fetchMock);

        const res = await GET(req());

        expect(res.status).toBe(200);
        const init = fetchMock.mock.calls[0]?.[1] as RequestInit;
        expect((init.headers as Record<string, string>).authorization).toBe('Bearer token-1');
    });

    it('treats a definitive 401 as signed out', async () => {
        const fetchMock = vi.fn().mockResolvedValue(new Response(null, {status: 401}));
        const {GET} = await loadRoute('https://api.example.com', fetchMock);

        const res = await GET(req());

        expect(res.status).toBe(200);
        await expect(res.json()).resolves.toEqual({signedIn: false});
    });

    it('returns 502 when the backend validate call fails', async () => {
        vi.spyOn(console, 'error').mockImplementation(() => undefined);
        const fetchMock = vi.fn().mockResolvedValue(new Response(null, {status: 503}));
        const {GET} = await loadRoute('https://api.example.com', fetchMock);

        const res = await GET(req());

        // An indeterminate failure must keep the client's last known auth state.
        expect(res.status).toBe(502);
        await expect(res.json()).resolves.toEqual({error: 'validate failed'});
    });

    it('returns 504 when the validate call times out', async () => {
        vi.spyOn(console, 'error').mockImplementation(() => undefined);
        const fetchMock = vi.fn().mockRejectedValue(new DOMException('timeout', 'TimeoutError'));
        const {GET} = await loadRoute('https://api.example.com', fetchMock);

        const res = await GET(req());

        expect(res.status).toBe(504);
        await expect(res.json()).resolves.toEqual({error: 'timeout'});
    });
});
