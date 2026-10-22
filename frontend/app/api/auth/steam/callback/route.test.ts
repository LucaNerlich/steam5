import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {NextRequest} from 'next/server';

vi.mock('next/cache', () => ({
    revalidatePath: vi.fn(),
    revalidateTag: vi.fn(),
}));

describe('steam callback origin gate', () => {
    beforeEach(() => {
        vi.resetModules();
        vi.stubEnv('NODE_ENV', 'production');
        vi.stubEnv('NEXT_PUBLIC_API_DOMAIN', 'http://backend:8080');
        vi.stubEnv('ALLOWED_BACKEND_ORIGINS', '');
        vi.stubGlobal('fetch', vi.fn());
    });

    afterEach(() => {
        vi.unstubAllEnvs();
        vi.unstubAllGlobals();
        vi.restoreAllMocks();
    });

    it('refuses to exchange the login ticket over an untrusted backend origin', async () => {
        vi.spyOn(console, 'error').mockImplementation(() => undefined);
        const {GET} = await import('./route');
        const request = new NextRequest('https://steam5.org/api/auth/steam/callback?state=abc&code=1', {
            headers: {cookie: 's5_state=abc'},
        });

        const response = await GET(request);

        expect(fetch).not.toHaveBeenCalled();
        expect(response.headers.get('location')).toContain('auth=failed');
        expect(response.cookies.get('s5_token')?.value ?? '').toBe('');
    });
});
