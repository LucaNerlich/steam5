import {describe, expect, it} from 'vitest';
import {focusWithinElement} from './focusGuards';

// Plain stubs — the vitest env has no DOM; only Element.contains is exercised.
function stubRoot(contains: (node: Node | null) => boolean): Element {
    return {contains} as unknown as Element;
}

describe('focusWithinElement', () => {
    it('is true only when the root contains the focused element', () => {
        const focused = {} as Element;
        const insideRoot = stubRoot((node) => node === focused);

        expect(focusWithinElement(insideRoot, focused)).toBe(true);
    });

    it('is false when focus sits outside the root (Escape pressed elsewhere)', () => {
        const outsideRoot = stubRoot(() => false);

        expect(focusWithinElement(outsideRoot, {} as Element)).toBe(false);
    });

    it('is false when the root or the focused element is missing', () => {
        const alwaysTrue = stubRoot(() => true);

        expect(focusWithinElement(alwaysTrue, null)).toBe(false);
        expect(focusWithinElement(null, {} as Element)).toBe(false);
        expect(focusWithinElement(null, null)).toBe(false);
    });
});
