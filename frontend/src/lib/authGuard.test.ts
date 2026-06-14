import {describe, expect, it} from 'vitest';
import {
    computeSignedOutDuringPlay,
    resolveLiveSignedIn,
    shouldWarnBeforeSubmit,
} from './authGuard';

describe('computeSignedOutDuringPlay', () => {
    it('is false when there is no submission state yet', () => {
        expect(computeSignedOutDuringPlay(true, null)).toBe(false);
        expect(computeSignedOutDuringPlay(true, undefined)).toBe(false);
    });

    it('is true when the backend rejected the cookie (unauthorized)', () => {
        // Even if the UI thought the user was signed out, an explicit 401 means
        // the cookie was present but invalid.
        expect(computeSignedOutDuringPlay(true, {ok: false, unauthorized: true})).toBe(true);
        expect(computeSignedOutDuringPlay(false, {ok: false, unauthorized: true})).toBe(true);
        expect(computeSignedOutDuringPlay(null, {ok: false, unauthorized: true})).toBe(true);
    });

    it('is true when the UI showed signed-in but the guess was not persisted', () => {
        // The reported bug: cookie dropped mid-round, guess silently went to the
        // anonymous endpoint (ok, but persisted === false) while the header said
        // "logged in".
        expect(computeSignedOutDuringPlay(true, {ok: true, persisted: false})).toBe(true);
    });

    it('is false for a genuine anonymous user (UI already knows they are signed out)', () => {
        // A user who was never signed in should not see a "you were signed out"
        // notice — that is the normal anonymous flow.
        expect(computeSignedOutDuringPlay(false, {ok: true, persisted: false})).toBe(false);
        expect(computeSignedOutDuringPlay(null, {ok: true, persisted: false})).toBe(false);
    });

    it('is false on the happy path (signed in and persisted)', () => {
        expect(computeSignedOutDuringPlay(true, {ok: true, persisted: true})).toBe(false);
    });

    it('is false for a non-auth error', () => {
        expect(computeSignedOutDuringPlay(true, {ok: false})).toBe(false);
    });
});

describe('resolveLiveSignedIn', () => {
    it('trusts a cached false without consulting the fetched value', () => {
        expect(resolveLiveSignedIn(false, true)).toBe(false);
        expect(resolveLiveSignedIn(false, false)).toBe(false);
    });

    it('defers to the fetched value when cached state is true (possibly stale)', () => {
        expect(resolveLiveSignedIn(true, false)).toBe(false);
        expect(resolveLiveSignedIn(true, true)).toBe(true);
    });

    it('defers to the fetched value when cached state is unknown (loading)', () => {
        expect(resolveLiveSignedIn(null, false)).toBe(false);
        expect(resolveLiveSignedIn(null, true)).toBe(true);
    });
});

describe('shouldWarnBeforeSubmit', () => {
    it('never warns when the user dismissed the warning', () => {
        expect(shouldWarnBeforeSubmit(true, false)).toBe(false);
        expect(shouldWarnBeforeSubmit(true, true)).toBe(false);
    });

    it('warns when not dismissed and the live state is signed out', () => {
        expect(shouldWarnBeforeSubmit(false, false)).toBe(true);
    });

    it('does not warn when not dismissed but the user is signed in', () => {
        expect(shouldWarnBeforeSubmit(false, true)).toBe(false);
    });
});
