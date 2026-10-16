import {describe, expect, it, vi} from 'vitest';
import {textCaption} from './GameInfoVideos';

// The suite runs in the node environment, so the single DOM API the caption formatter
// touches is stubbed: an element with the two properties that decide whether a caption
// becomes text or markup. innerHTML is recorded so a regression to the default Fancybox
// renderer (string caption through innerHTML) fails loudly.
class FakeElement {
    textContent: string | null = null;
    innerHTML: string | null = null;
}

vi.stubGlobal('document', {createElement: () => new FakeElement()});

describe('textCaption', () => {
    it('carries markup-bearing movie names as text, never as markup', () => {
        const caption = '<img src=x onerror=alert(document.cookie)>';
        const el = textCaption(caption) as unknown as FakeElement;

        expect(el.textContent).toBe(caption);
        expect(el.innerHTML).toBeNull();
    });

    it('renders a missing caption as empty text', () => {
        const el = textCaption(undefined) as unknown as FakeElement;

        expect(el.textContent).toBe('');
        expect(el.innerHTML).toBeNull();
    });
});
