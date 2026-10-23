import {afterEach, describe, expect, it, vi} from 'vitest';

describe('BACKEND_ORIGIN', () => {
    afterEach(() => {
        vi.unstubAllEnvs();
        vi.resetModules();
    });

    it('strips a trailing slash so every caller builds a single-slash path', async () => {
        vi.stubEnv('NEXT_PUBLIC_API_DOMAIN', 'https://api.example.com/');
        vi.resetModules();
        const {BACKEND_ORIGIN} = await import('./backend');
        expect(BACKEND_ORIGIN).toBe('https://api.example.com');
    });
});
