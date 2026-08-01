import {afterEach, describe, expect, it, vi} from 'vitest';
import {isTrustedBrowserOrigin, trustedSiteOrigin} from './requestOrigin';

describe('isTrustedBrowserOrigin', () => {
    afterEach(() => {
        vi.unstubAllEnvs();
    });

    it('accepts a matching Origin', () => {
        vi.stubEnv('NEXT_PUBLIC_DOMAIN', 'https://steam5.org');
        const headers = new Headers({origin: 'https://steam5.org'});
        expect(isTrustedBrowserOrigin(headers)).toBe(true);
        expect(trustedSiteOrigin()).toBe('https://steam5.org');
    });

    it('accepts a matching Referer when Origin is absent', () => {
        vi.stubEnv('NEXT_PUBLIC_DOMAIN', 'https://steam5.org');
        const headers = new Headers({referer: 'https://steam5.org/review-guesser/1'});
        expect(isTrustedBrowserOrigin(headers)).toBe(true);
    });

    it('rejects a foreign Origin', () => {
        vi.stubEnv('NEXT_PUBLIC_DOMAIN', 'https://steam5.org');
        const headers = new Headers({origin: 'https://evil.example'});
        expect(isTrustedBrowserOrigin(headers)).toBe(false);
    });

    it('rejects missing Origin and Referer', () => {
        vi.stubEnv('NEXT_PUBLIC_DOMAIN', 'https://steam5.org');
        expect(isTrustedBrowserOrigin(new Headers())).toBe(false);
    });

    it('allows localhost in non-production', () => {
        vi.stubEnv('NEXT_PUBLIC_DOMAIN', 'https://steam5.org');
        vi.stubEnv('NODE_ENV', 'development');
        const headers = new Headers({origin: 'http://localhost:3000'});
        expect(isTrustedBrowserOrigin(headers)).toBe(true);
    });

    it('rejects localhost in production', () => {
        vi.stubEnv('NEXT_PUBLIC_DOMAIN', 'https://steam5.org');
        vi.stubEnv('NODE_ENV', 'production');
        const headers = new Headers({origin: 'http://localhost:3000'});
        expect(isTrustedBrowserOrigin(headers)).toBe(false);
    });
});
