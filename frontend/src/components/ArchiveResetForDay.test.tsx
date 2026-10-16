import {afterEach, describe, expect, it, vi} from 'vitest';
import {renderToStaticMarkup} from 'react-dom/server';
import ArchiveResetForDay from './ArchiveResetForDay';

afterEach(() => {
    vi.unstubAllGlobals();
});

describe('ArchiveResetForDay', () => {
    it('does not read localStorage during the server render', () => {
        // The server cannot see localStorage; touching it while rendering is what made the
        // server emit no reset controls while the client hydration render added them.
        const key = vi.fn(() => 'review-guesser:2026-10-10');
        const getItem = vi.fn(() => JSON.stringify({results: {1: {appId: 1}}}));
        vi.stubGlobal('localStorage', {length: 1, key, getItem});

        const html = renderToStaticMarkup(<ArchiveResetForDay date="2026-10-10"/>);

        expect(html).not.toContain('Reset this day');
        expect(html).not.toContain('Reset all');
        expect(key).not.toHaveBeenCalled();
        expect(getItem).not.toHaveBeenCalled();
    });
});
