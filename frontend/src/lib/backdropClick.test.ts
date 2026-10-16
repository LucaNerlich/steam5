import {describe, expect, it} from 'vitest';
import {isBackdropClick} from './backdropClick';

// The vitest suite runs in a plain node environment (no DOM), so the "element"
// graph is faked with plain objects exposing just what `isBackdropClick`
// observably relies on: reference identity and Element.contains().
function makeTrackedContent(descendants: readonly unknown[]): Element {
    const content = {
        // A node contains itself (Element.contains semantics), which is what
        // keeps hits on the content element's own padding out of "backdrop".
        contains: (node: unknown): boolean => node === content || descendants.includes(node),
    };
    return content as unknown as Element;
}

describe('isBackdropClick', () => {
    const dialog = {id: 'dialog'} as unknown as HTMLDialogElement;

    it('treats a click on the dialog element itself as a backdrop click', () => {
        // Clicks on the ::backdrop pseudo-element are retargeted to the
        // <dialog> element by the browser — the only signal available.
        expect(isBackdropClick(dialog, dialog, null)).toBe(true);
    });

    it('ignores clicks inside the tracked content element', () => {
        // Heading/button inside the modal body.
        const heading = {id: 'heading'} as unknown as EventTarget;
        const content = makeTrackedContent([heading]);
        expect(isBackdropClick(heading, dialog, content)).toBe(false);
    });

    it('never counts a click on the content element itself as a backdrop click', () => {
        // Issue #355: when the 16px padding lives on the tracked content div
        // (not on the dialog), the padding ring is part of the card. A click
        // in that ring targets the content element and is NOT a backdrop
        // click — it must not cancel the modal / drop a pending guess.
        const content = makeTrackedContent([]);
        expect(isBackdropClick(content, dialog, content)).toBe(false);
    });

    it('treats clicks outside the tracked content as backdrop clicks', () => {
        // Preserved legacy behavior: anything outside the tracked content
        // (that is not the dialog) still dismisses the modal.
        const content = makeTrackedContent([]);
        const stray = {id: 'stray'} as unknown as EventTarget;
        expect(isBackdropClick(stray, dialog, content)).toBe(true);
    });

    it('without a tracked content element, only dialog clicks dismiss', () => {
        const stray = {id: 'stray'} as unknown as EventTarget;
        expect(isBackdropClick(stray, dialog, null)).toBe(false);
        expect(isBackdropClick(dialog, dialog, null)).toBe(true);
    });
});
