import {describe, expect, it} from 'vitest';
import sitemap from './sitemap';

/**
 * The sitemap is generated from the central `Routes` map minus a hand-maintained exclusion set,
 * so nothing noticed when a page started declaring `robots: {index:false}` while still being
 * advertised. This pins both halves of the exclusion: redirecting routes and noindex pages.
 */
describe('sitemap', () => {
    const baseUrl = (process.env.NEXT_PUBLIC_DOMAIN || 'https://steam5.org').replace(/\/$/, '');

    it('advertises the indexable pages', async () => {
        const urls = (await sitemap()).map((entry) => entry.url);

        expect(urls).toContain(`${baseUrl}/review-guesser/leaderboard`);
        expect(urls).toContain(`${baseUrl}/review-guesser/1`);
    });

    it('omits the pages that declare themselves noindex', async () => {
        const urls = (await sitemap()).map((entry) => entry.url);

        // privacy/page.tsx and imprint/page.tsx both set robots: { index: false }.
        expect(urls).not.toContain(`${baseUrl}/privacy`);
        expect(urls).not.toContain(`${baseUrl}/imprint`);
    });

    it('omits the redirecting routes', async () => {
        const urls = (await sitemap()).map((entry) => entry.url);

        expect(urls).not.toContain(`${baseUrl}/`);
        expect(urls).not.toContain(`${baseUrl}/review-guesser`);
        expect(urls).not.toContain(`${baseUrl}/review-guesser/random`);
    });
});
