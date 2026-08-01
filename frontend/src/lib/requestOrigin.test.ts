import {afterEach, describe, expect, it} from 'vitest';
import {isTrustedBrowserOrigin, trustedSiteOrigin} from './requestOrigin';

describe('isTrustedBrowserOrigin', () => {
    const prev = process.env.NEXT_PUBLIC_DOMAIN;

    afterEach(() => {
        if (prev === undefined) delete process.env.NEXT_PUBLIC_DOMAIN;
        else process.env.NEXT_PUBLIC_DOMAIN = prev;
    });

    it('accepts a matching Origin', () => {
        process.env.NEXT_PUBLIC_DOMAIN = 'https://steam5.org';
        const headers = new Headers({origin: 'https://steam5.org'});
        expect(isTrustedBrowserOrigin(headers)).toBe(true);
        expect(trustedSiteOrigin()).toBe('https://steam5.org');
    });

    it('accepts a matching Referer when Origin is absent', () => {
        process.env.NEXT_PUBLIC_DOMAIN = 'https://steam5.org';
        const headers = new Headers({referer: 'https://steam5.org/review-guesser/1'});
        expect(isTrustedBrowserOrigin(headers)).toBe(true);
    });

    it('rejects a foreign Origin', () => {
        process.env.NEXT_PUBLIC_DOMAIN = 'https://steam5.org';
        const headers = new Headers({origin: 'https://evil.example'});
        expect(isTrustedBrowserOrigin(headers)).toBe(false);
    });

    it('rejects missing Origin and Referer', () => {
        process.env.NEXT_PUBLIC_DOMAIN = 'https://steam5.org';
        expect(isTrustedBrowserOrigin(new Headers())).toBe(false);
    });

    it('allows localhost during local development', () => {
        process.env.NEXT_PUBLIC_DOMAIN = 'https://steam5.org';
        const headers = new Headers({origin: 'http://localhost:3000'});
        expect(isTrustedBrowserOrigin(headers)).toBe(true);
    });
});
