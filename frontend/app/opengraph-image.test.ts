import {describe, expect, it} from 'vitest';
import {sanitizeAvatarUrl} from './opengraph-image';

describe('sanitizeAvatarUrl', () => {
    it('allows a real Steam CDN avatar URL', () => {
        expect(sanitizeAvatarUrl('https://avatars.akamai.steamstatic.com/abc.jpg'))
            .toBe('https://avatars.akamai.steamstatic.com/abc.jpg');
    });

    it('allows the legacy akamaihd.net Steam CDN host', () => {
        expect(sanitizeAvatarUrl('https://avatars.akamaihd.net/abc.jpg'))
            .toBe('https://avatars.akamaihd.net/abc.jpg');
    });

    it('rejects an arbitrary attacker-controlled host (SSRF)', () => {
        expect(sanitizeAvatarUrl('https://internal-service.local/admin')).toBeNull();
    });

    it('rejects a host that merely contains the allowed suffix as a prefix trick', () => {
        expect(sanitizeAvatarUrl('https://steamstatic.com.evil.example/x.jpg')).toBeNull();
    });

    it('rejects a non-https scheme even on an otherwise allowed host', () => {
        expect(sanitizeAvatarUrl('http://avatars.steamstatic.com/abc.jpg')).toBeNull();
    });

    it('rejects a malformed URL instead of throwing', () => {
        expect(sanitizeAvatarUrl('not-a-url')).toBeNull();
    });

    it('rejects null/undefined/empty', () => {
        expect(sanitizeAvatarUrl(null)).toBeNull();
        expect(sanitizeAvatarUrl(undefined)).toBeNull();
        expect(sanitizeAvatarUrl('')).toBeNull();
    });
});
