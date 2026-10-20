import {afterEach, describe, expect, it, vi} from 'vitest';

afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
});

describe('archive/season loaders distinguish missing from unavailable', () => {
    it('returns null for a missing archive day and throws for a 5xx', async () => {
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
});
