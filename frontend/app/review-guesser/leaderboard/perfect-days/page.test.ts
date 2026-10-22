import {describe, expect, it, vi} from 'vitest';
import {renderToStaticMarkup} from 'react-dom/server';

vi.mock('@/lib/perfectDays', () => ({
    // The backend sends a refresh time; formatting it during the prerender is what made the
    // server HTML disagree with the visitor's timezone.
    fetchPerfectDays: vi.fn(async () => ({data: [], refreshedAt: '2026-07-24T00:50:00Z'})),
}));

describe('perfect-days page', () => {
    it('does not format the refresh time into the server HTML', async () => {
        const {default: PerfectDaysPage} = await import('./page');

        const html = renderToStaticMarkup(await PerfectDaysPage());

        expect(html).not.toContain('Last updated');
    });
});
