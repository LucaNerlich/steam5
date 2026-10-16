import {afterEach, describe, expect, it, vi} from 'vitest';
import {allowedBackendOrigin, isLoopbackOrigin, isTrustedBrowserOrigin, rejectUntrustedOrigin, trustedSiteOrigin} from './requestOrigin';

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

describe('rejectUntrustedOrigin', () => {
    afterEach(() => {
        vi.unstubAllEnvs();
        vi.restoreAllMocks();
    });

    it('returns null for a trusted origin', () => {
        vi.stubEnv('NEXT_PUBLIC_DOMAIN', 'https://steam5.org');
        const headers = new Headers({origin: 'https://steam5.org'});
        expect(rejectUntrustedOrigin(headers, 'comments/archive', '42')).toBeNull();
    });

    it('returns 403 no-store and logs sanitized fields when rejected', async () => {
        vi.stubEnv('NEXT_PUBLIC_DOMAIN', 'https://steam5.org');
        const errorSpy = vi.spyOn(console, 'error').mockImplementation(() => {});
        const headers = new Headers({
            origin: 'https://evil.example',
            referer: 'https://evil.example/path',
        });

        // commentId may come from the path param (not Headers), so control chars are possible.
        const response = rejectUntrustedOrigin(headers, 'comments/reactions', '7\u0001bad');
        expect(response).not.toBeNull();
        expect(response!.status).toBe(403);
        expect(response!.headers.get('Cache-Control')).toBe('private, no-store');
        await expect(response!.json()).resolves.toEqual({error: 'forbidden'});
        expect(errorSpy).toHaveBeenCalledWith(
            '[comments/reactions] Rejected untrusted origin',
            expect.objectContaining({
                commentId: '7bad',
                origin: 'https://evil.example',
                referer: 'https://evil.example/path',
            }),
        );
    });
});

describe('isLoopbackOrigin', () => {
    it('recognises an IPv6-loopback origin, whose hostname keeps its brackets', () => {
        // URL#hostname returns "[::1]", so a `::1` comparison can never match — the two api routes
        // that had their own copies compared exactly that, silently losing the loopback exemption.
        expect(isLoopbackOrigin('http://[::1]:8080')).toBe(true);
        expect(new URL('http://[::1]:8080').hostname).toBe('[::1]');
    });

    it('recognises the IPv4 and hostname forms', () => {
        expect(isLoopbackOrigin('http://localhost:8080')).toBe(true);
        expect(isLoopbackOrigin('http://127.0.0.1:8080')).toBe(true);
    });

    it('rejects remote and malformed origins', () => {
        expect(isLoopbackOrigin('https://steam5.org')).toBe(false);
        expect(isLoopbackOrigin('not a URL')).toBe(false);
    });
});

describe('allowedBackendOrigin', () => {
    afterEach(() => {
        vi.unstubAllEnvs();
    });

    it('accepts https, loopback and development origins', () => {
        expect(allowedBackendOrigin('https://api.steam5.org', true, '')).toBe('https://api.steam5.org');
        expect(allowedBackendOrigin('http://localhost:8080', true, '')).toBe('http://localhost:8080');
        expect(allowedBackendOrigin('http://backend:8080', false, '')).toBe('http://backend:8080');
    });

    it('accepts a trusted origin written with a trailing slash or an explicit port', () => {
        // The ws/ticket route compared raw strings and refused these, while logout accepted them:
        // one gate, one notion of "the same origin".
        expect(allowedBackendOrigin('http://backend:8080', true, 'http://backend:8080/')).toBe('http://backend:8080');
        expect(allowedBackendOrigin('http://backend:8080', true, 'HTTP://BACKEND:8080')).toBe('http://backend:8080');
    });

    it('refuses an untrusted plain-http origin in production', () => {
        expect(allowedBackendOrigin('http://backend:8080', true, 'https://other.example.com')).toBeNull();
    });

    it('refuses values that are not a bare http(s) origin', () => {
        expect(allowedBackendOrigin('http://user:pass@backend:8080', true, 'http://user:pass@backend:8080')).toBeNull();
        expect(allowedBackendOrigin('http://backend:8080/path', true, 'http://backend:8080/path')).toBeNull();
        expect(allowedBackendOrigin('not a url', true, 'not a url')).toBeNull();
    });
});
