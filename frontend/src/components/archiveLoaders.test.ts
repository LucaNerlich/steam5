import {afterEach, describe, expect, it, vi} from 'vitest';

afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
});

describe('archive/season loaders distinguish missing from unavailable', () => {
    it('returns null for a missing archive day and throws for a 5xx', {timeout: 20000}, async () => {
        const {loadArchived} = await import('../../app/review-guesser/archive/[date]/page');
        vi.stubGlobal('fetch', vi.fn(async () => new Response('gone', {status: 404})));
        await expect(loadArchived('2026-01-01')).resolves.toBeNull();

        vi.stubGlobal('fetch', vi.fn(async () => new Response('boom', {status: 503})));
        await expect(loadArchived('2026-01-01')).rejects.toThrow('503');

        vi.stubGlobal('fetch', vi.fn(async () => { throw new Error('network'); }));
        await expect(loadArchived('2026-01-01')).rejects.toThrow('network');
    });

    it('treats a malformed archive date (backend 400) as not found', async () => {
        const {loadArchived} = await import('../../app/review-guesser/archive/[date]/page');
        vi.stubGlobal('fetch', vi.fn(async () => new Response('bad', {status: 400})));
        await expect(loadArchived('2025-10-1')).resolves.toBeNull();
    });

    it('keeps the archive route revalidating hourly so a cached notFound heals', {timeout: 20000}, async () => {
        // notFound() depends on today's UTC date; a year-long revalidate kept serving the
        // day-specific 404 after the day ended.
        const page = await import('../../app/review-guesser/archive/[date]/page');
        expect(page.revalidate).toBeLessThanOrEqual(3600);
    });

    it('answers {} only for an absent day and throws on a real failure', {timeout: 20000}, async () => {
        // The throw is the whole fix: a swallowed 5xx used to be baked into the page cache.
        const {loadAnswers} = await import('../../app/review-guesser/archive/[date]/page');
        vi.stubGlobal('fetch', vi.fn(async () => new Response('gone', {status: 404})));
        await expect(loadAnswers('2026-01-01')).resolves.toEqual({});

        vi.stubGlobal('fetch', vi.fn(async () => new Response('boom', {status: 503})));
        await expect(loadAnswers('2026-01-01')).rejects.toThrow('503');

        vi.stubGlobal('fetch', vi.fn(async () => new Response('"nope"', {status: 200})));
        await expect(loadAnswers('2026-01-01')).rejects.toThrow('Unexpected answers payload');
    });

    it('throws when the archive month endpoint fails', async () => {
        const {loadMonthArchiveDays} = await import('../../app/review-guesser/archive/page');
        vi.stubGlobal('fetch', vi.fn(async () => new Response('boom', {status: 500})));
        await expect(loadMonthArchiveDays('2026-01', 60)).rejects.toThrow('500');
    });

    it('returns null for a missing season and throws for a 5xx', async () => {
        const {fetchSeasonDetail} = await import('../../app/review-guesser/seasons/[seasonNumber]/page');
        vi.stubGlobal('fetch', vi.fn(async () => new Response('gone', {status: 404})));
        await expect(fetchSeasonDetail('1')).resolves.toBeNull();
        vi.stubGlobal('fetch', vi.fn(async () => new Response('boom', {status: 503})));
        await expect(fetchSeasonDetail('1')).rejects.toThrow('503');
    });

    it('distinguishes a failed seasons list from an empty one', async () => {
        const {loadSeasons} = await import('../../app/review-guesser/seasons/page');

        vi.stubGlobal('fetch', vi.fn(async () => new Response('boom', {status: 503})));
        await expect(loadSeasons()).resolves.toBeNull();

        vi.stubGlobal('fetch', vi.fn(async () => Response.json([])));
        await expect(loadSeasons()).resolves.toEqual([]);
    });
});
