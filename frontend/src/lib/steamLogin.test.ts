import {describe, expect, it, vi} from 'vitest';
import {startSteamLogin} from './steamLogin';

// Node environment: the two browser globals this helper drives are stubbed. The href on
// the location stub is what the helper "navigates" to, and the cookie stub is what it
// sets, so the assertion is the real contract the callback route checks.
const locationStub = {protocol: 'https:', origin: 'https://steam5.org', href: ''};
const documentStub = {cookie: ''};

vi.stubGlobal('window', {location: locationStub});
vi.stubGlobal('document', documentStub);

describe('startSteamLogin', () => {
    it('sets the s5_state cookie and carries the same state in the login URL', () => {
        documentStub.cookie = '';
        locationStub.href = '';

        startSteamLogin();

        const match = /s5_state=([a-z0-9]+)/.exec(documentStub.cookie);
        expect(match).not.toBeNull();
        expect(documentStub.cookie).toContain('path=/');
        expect(documentStub.cookie).toContain('max-age=300');
        expect(documentStub.cookie).toContain('samesite=lax');
        expect(documentStub.cookie).toContain('Secure');

        expect(locationStub.href).toContain('/api/auth/steam/login?redirect=');
        expect(locationStub.href).toContain(`&state=${match![1]}`);
    });

    it('mints a different state for every login attempt', () => {
        documentStub.cookie = '';
        startSteamLogin();
        const first = documentStub.cookie;

        documentStub.cookie = '';
        startSteamLogin();

        expect(documentStub.cookie).not.toBe(first);
    });
});
