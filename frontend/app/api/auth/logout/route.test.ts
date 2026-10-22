import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {NextRequest} from 'next/server';

vi.mock('next/cache', () => ({
    revalidatePath: vi.fn(),
    revalidateTag: vi.fn(),
}));

import {POST} from './route';

describe('POST', () => {
    beforeEach(() => {
        vi.stubEnv('NODE_ENV', 'production');
        vi.stubEnv('NEXT_PUBLIC_API_DOMAIN', 'http://api.example.com');
        vi.stubEnv('ALLOWED_BACKEND_ORIGINS', '');
        vi.stubGlobal('fetch', vi.fn());
    });

    afterEach(() => {
        vi.unstubAllEnvs();
        vi.unstubAllGlobals();
        vi.restoreAllMocks();
    });

    it('never sends the session credential to a disallowed HTTP backend', async () => {
        vi.spyOn(console, 'error').mockImplementation(() => undefined);
        const request = new NextRequest('https://steam5.org/api/auth/logout', {
            method: 'POST',
            // A same-origin browser POST navigation carries Origin; the guard requires it.
            headers: {cookie: 's5_token=secret-token', origin: 'https://steam5.org'},
        });

        const response = await POST(request);

        expect(fetch).not.toHaveBeenCalled();
        // 303, not the 307 default: the browser must continue with GET, not re-POST the page.
        expect(response.status).toBe(303);
        expect(response.headers.get('location')).toBe('https://steam5.org/review-guesser/1');
        expect(response.cookies.get('s5_token')?.value).toBe('');
    });

    it('relays the client IP on the token revocation call', async () => {
        vi.stubEnv('NEXT_PUBLIC_API_DOMAIN', 'https://api.example.com');
        vi.mocked(fetch).mockResolvedValue(new Response(null, {status: 200}));
        const request = new NextRequest('https://steam5.org/api/auth/logout', {
            method: 'POST',
            headers: {
                cookie: 's5_token=secret-token',
                origin: 'https://steam5.org',
                'x-forwarded-for': '203.0.113.7',
            },
        });

        await POST(request);

        const [, init] = vi.mocked(fetch).mock.calls[0];
        expect((init?.headers as Record<string, string>)['x-forwarded-for']).toBe('203.0.113.7');
    });

    it('logs a non-2xx revocation and still completes the best-effort logout', async () => {
        vi.stubEnv('NEXT_PUBLIC_API_DOMAIN', 'https://api.example.com');
        const errorSpy = vi.spyOn(console, 'error').mockImplementation(() => undefined);
        vi.mocked(fetch).mockResolvedValue(new Response(null, {status: 429}));
        const request = new NextRequest('https://steam5.org/api/auth/logout', {
            method: 'POST',
            headers: {cookie: 's5_token=secret-token', origin: 'https://steam5.org'},
        });

        const response = await POST(request);

        expect(errorSpy.mock.calls.flat().join(' ')).toContain('429');
        expect(response.status).toBe(303);
        expect(response.cookies.get('s5_token')?.value).toBe('');
    });

    it('rejects a cross-site logout before touching the session', async () => {
        vi.spyOn(console, 'error').mockImplementation(() => undefined);
        const request = new NextRequest('https://steam5.org/api/auth/logout', {
            method: 'POST',
            headers: {cookie: 's5_token=secret-token', origin: 'https://evil.example'},
        });

        const response = await POST(request);

        expect(response.status).toBe(403);
        // The session must survive a forced logout: no cookie clear, no redirect, no revocation call.
        expect(response.cookies.get('s5_token')).toBeUndefined();
        expect(response.headers.get('location')).toBeNull();
        expect(fetch).not.toHaveBeenCalled();
    });
});
