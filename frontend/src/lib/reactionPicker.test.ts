import {describe, expect, it} from 'vitest';
import {nextOpenPickerId} from './reactionPicker';

describe('nextOpenPickerId', () => {
    it('opens picker A from a closed state', () => {
        expect(nextOpenPickerId(null, 1, true)).toBe(1);
    });

    it('switches from picker A to picker B without an explicit close of A', () => {
        // Simulates keyboard activation of B's trigger: B reports isOpen=true
        // while A is still recorded as open, and no close-A signal ever fires
        // (Tab+Enter on B's button doesn't dispatch the mousedown/touchstart
        // event that the outside-click listener relies on).
        const afterAOpens = nextOpenPickerId(null, 1, true);
        const afterBOpens = nextOpenPickerId(afterAOpens, 2, true);
        expect(afterBOpens).toBe(2);
    });

    it('closes picker B when it reports isOpen=false', () => {
        const state = nextOpenPickerId(2, 2, false);
        expect(state).toBeNull();
    });

    it('ignores a stale close signal from a picker that is no longer active', () => {
        // A's unmount/outside-click cleanup can fire after B has already
        // become the active picker; that stale signal must not clear B.
        const afterBOpen = nextOpenPickerId(1, 2, true);
        const afterStaleAClose = nextOpenPickerId(afterBOpen, 1, false);
        expect(afterStaleAClose).toBe(2);
    });
});
