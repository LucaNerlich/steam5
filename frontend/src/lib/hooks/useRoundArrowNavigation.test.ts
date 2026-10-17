import {afterEach, describe, expect, it, vi} from 'vitest';
import {hasOpenModalDialog} from './useRoundArrowNavigation';

// The DOM semantics the selector has to distinguish: `dialog[open]` matches any open dialog,
// `dialog:modal` only one opened with showModal(). A non-modal `<dialog open>` (the round
// result card) is in the document for the whole visit.
function documentWith(state: {inlineDialogOpen?: boolean; modalOpen?: boolean}) {
    return {
        querySelector: (selector: string) => {
            if (selector === 'dialog:modal') return state.modalOpen ? {} : null;
            if (selector === 'dialog[open]') return state.modalOpen || state.inlineDialogOpen ? {} : null;
            return null;
        },
    };
}

afterEach(() => {
    vi.unstubAllGlobals();
});

describe('hasOpenModalDialog', () => {
    it('reports no modal for the inline, non-modal result card', () => {
        // The regression: `dialog[open]` matched the always-open result card and killed the
        // arrow-key round navigation for every player who had a result on screen.
        vi.stubGlobal('document', documentWith({inlineDialogOpen: true}));

        expect(hasOpenModalDialog()).toBe(false);
    });

    it('reports a modal while the lightbox or an app modal is open', () => {
        vi.stubGlobal('document', documentWith({modalOpen: true}));

        expect(hasOpenModalDialog()).toBe(true);
    });

    it('reports no modal when nothing is open', () => {
        vi.stubGlobal('document', documentWith({}));

        expect(hasOpenModalDialog()).toBe(false);
    });
});
