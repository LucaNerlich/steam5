import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {NextRequest} from 'next/server';

vi.mock('next/cache', () => ({
    revalidatePath: vi.fn(),
    revalidateTag: vi.fn(),
}));

import {allowedLogoutBackendOrigin, POST} from './route';

describe('allowedLogoutBackendOrigin', () => {
    it('requires HTTPS for a remote backend in production', () => {
        expect(allowedLogoutBackendOrigin('http://api.example.com', true, '')).toBeNull();
        expect(allowedLogoutBackendOrigin('https://api.example.com', true, '')).toBe('https://api.example.com');
    });

    it('allows development, loopback, and explicitly trusted HTTP services', () => {
        expect(allowedLogoutBackendOrigin('http://api.example.com', false, '')).toBe('http://api.example.com');
        expect(allowedLogoutBackendOrigin('http://localhost:8080', true, '')).toBe('http://localhost:8080');
        expect(allowedLogoutBackendOrigin(
            'http://backend:8080', true, 'https://other.example.com, http://backend:8080',
        )).toBe('http://backend:8080');
    });

    it('rejects malformed origins and URLs containing credentials or paths', () => {
        expect(allowedLogoutBackendOrigin('not a URL', false, '')).toBeNull();
        expect(allowedLogoutBackendOrigin('https://user:pass@example.com', false, '')).toBeNull();
        expect(allowedLogoutBackendOrigin('https://example.com/backend', false, '')).toBeNull();
    });
});

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
            headers: {cookie: 's5_token=secret-token'},
        });

        const response = await POST(request);

        expect(fetch).not.toHaveBeenCalled();
        expect(response.headers.get('location')).toBe('https://steam5.org/review-guesser/1');
        expect(response.cookies.get('s5_token')?.value).toBe('');
    });
});
