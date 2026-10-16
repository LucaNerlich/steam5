import {afterEach, describe, expect, it, vi} from 'vitest';
import {NextResponse} from 'next/server';
import sitemap from './sitemap';

afterEach(() => {
    vi.unstubAllGlobals();
    vi.unstubAllEnvs();
});

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

    it('emits the archived challenge dates the backend reports', async () => {
        // A build that cannot reach the backend silently shipped an empty archive section; the
        // loader now warns, and this pins that the dated entries are emitted when they exist.
        const archiveDate = '2026-10-01';
        vi.stubGlobal('fetch', vi.fn(async () => NextResponse.json([archiveDate])));
        vi.stubEnv('NEXT_PUBLIC_API_DOMAIN', 'https://api.example.com');

        const urls = (await sitemap()).map((entry) => entry.url);

        expect(urls).toContain(`${baseUrl}/review-guesser/archive/${archiveDate}`);
        vi.unstubAllGlobals();
    });

    it('keeps serving the last good dates when backend access fails during a revalidation', async () => {
        // Module-level date state survives between the regenerations of one process, like the
        // ISR revalidations within one app instance. vi.resetModules() plus a dynamic import
        // keeps this test off the statically imported module instance that the other tests
        // share, so it starts from a clean cache.
        vi.resetModules();
        const primaryDate = '2026-09-15';
        vi.stubGlobal('fetch', vi.fn(async () => NextResponse.json([primaryDate])));
        vi.stubEnv('NEXT_PUBLIC_API_DOMAIN', 'https://api.example.com');

        // First generation reaches the backend and learns the date...
        const {default: isolated} = await import('./sitemap');
        expect((await isolated()).map((entry) => entry.url))
            .toContain(`${baseUrl}/review-guesser/archive/${primaryDate}`);

        // ...while the next one falls into the failure path. The already-learned dates must
        // stay in the served sitemap instead of all archive URLs vanishing for a revalidate
        // window.
        vi.stubGlobal('fetch', vi.fn(async () => {
            throw new TypeError('fetch failed');
        }));
        expect((await isolated()).map((entry) => entry.url))
            .toContain(`${baseUrl}/review-guesser/archive/${primaryDate}`);
    });

    it('omits the redirecting routes', async () => {
        const urls = (await sitemap()).map((entry) => entry.url);

        expect(urls).not.toContain(`${baseUrl}/`);
        expect(urls).not.toContain(`${baseUrl}/review-guesser`);
        expect(urls).not.toContain(`${baseUrl}/review-guesser/random`);
    });
});
