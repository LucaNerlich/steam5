import {afterEach, describe, expect, it, vi} from 'vitest';
import {NextRequest, NextResponse} from 'next/server';
import {GET} from './route';

const req = (timeframe: string) =>
    new NextRequest(`https://steam5.org/api/leaderboard/achievements?timeframe=${timeframe}`);

afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
});

describe('GET achievements timeframe', () => {
    it('passes monthly through with the backend 1h TTL', async () => {
        const fetchMock = vi.fn(async () => NextResponse.json([]));
        vi.stubGlobal('fetch', fetchMock);

        await GET(req('monthly'));

        const [url, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
        expect(url).toContain('timeframe=monthly');
        expect((init.next as {revalidate: number}).revalidate).toBe(3600);
    });

    it('maps the backend today alias to daily', async () => {
        const fetchMock = vi.fn(async () => NextResponse.json([]));
        vi.stubGlobal('fetch', fetchMock);

        await GET(req('today'));

        const [url, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
        expect(url).toContain('timeframe=daily');
        expect((init.next as {revalidate: number}).revalidate).toBe(300);
    });

    it('lowercases a case variant', async () => {
        const fetchMock = vi.fn(async () => NextResponse.json([]));
        vi.stubGlobal('fetch', fetchMock);

        await GET(req('DAILY'));

        const [url] = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
        expect(url).toContain('timeframe=daily');
    });

    it('normalizes an unknown timeframe to all-time instead of minting a 24h key', async () => {
        const fetchMock = vi.fn(async () => NextResponse.json([]));
        vi.stubGlobal('fetch', fetchMock);

        const res = await GET(req('not-a-timeframe'));

        expect(res.status).toBe(200);
        const [url, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
        expect(url).toContain('timeframe=all');
        expect((init.next as {revalidate: number}).revalidate).toBe(86400);
    });
});
