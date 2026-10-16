import {afterEach, describe, expect, it, vi} from 'vitest';
import {NextRequest, NextResponse} from 'next/server';
import {GET} from './route';

const req = () => new NextRequest('https://steam5.org/api/review-game/next-challenge-time');

afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
});

describe('GET', () => {
    it('keeps the public TTL on a successful response', async () => {
        vi.stubGlobal('fetch', vi.fn(async () => NextResponse.json({nextChallengeTime: '2026-10-17T00:00:00Z'})));

        const res = await GET(req());

        expect(res.status).toBe(200);
        expect(res.headers.get('cache-control')).toBe('public, s-maxage=60, max-age=30');
    });

    it('does not mark an error response publicly cacheable', async () => {
        // The TTL used to be emitted from both branches, so a backend blip pinned the missing
        // line for everyone for a minute in any shared cache in front of the app.
        vi.spyOn(console, 'error').mockImplementation(() => undefined);
        vi.stubGlobal('fetch', vi.fn(async () => new NextResponse('boom', {status: 503})));

        const res = await GET(req());

        expect(res.status).toBe(503);
        expect(res.headers.get('cache-control')).toBe('no-store');
    });
});
