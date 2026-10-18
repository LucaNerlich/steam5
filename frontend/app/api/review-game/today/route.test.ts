import {afterEach, describe, expect, it, vi} from 'vitest';
import {NextRequest, NextResponse} from 'next/server';
import {BACKEND_TIMEOUT_MS} from '@/lib/backend';
import {GET} from './route';

const req = () => new NextRequest('https://steam5.org/api/review-game/today');

afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
});

describe('GET', () => {
    it('bounds the upstream call with the shared backend timeout', async () => {
        const timeoutSpy = vi.spyOn(AbortSignal, 'timeout');
        const fetchMock = vi.fn(async (_url: string | URL | Request, _init?: RequestInit) =>
            NextResponse.json({ok: true}));
        vi.stubGlobal('fetch', fetchMock);

        const res = await GET(req());

        expect(res.status).toBe(200);
        expect(timeoutSpy).toHaveBeenCalledWith(BACKEND_TIMEOUT_MS);
        // The signal fetch receives must be the one created from the shared bound.
        const init = fetchMock.mock.calls[0][1] as RequestInit;
        expect(init.signal).toBe(timeoutSpy.mock.results[0]?.value);
    });

    it('answers 502 when the bounded backend call aborts', async () => {
        // The route hardcodes BACKEND_TIMEOUT_MS (10 s): shrinking the returned bound keeps the
        // sibling bounded-502 pattern (src/lib/backend.test.ts) cheap instead of a real 10 s wait.
        const realTimeout = AbortSignal.timeout.bind(AbortSignal);
        vi.spyOn(AbortSignal, 'timeout').mockImplementation((ms: number) => realTimeout(5));
        vi.spyOn(console, 'error').mockImplementation(() => undefined);
        vi.stubGlobal('fetch', vi.fn((_url: string | URL | Request, init?: RequestInit) =>
            new Promise((_resolve, reject) => {
                init?.signal?.addEventListener('abort', () => reject(new Error('aborted')));
            })));

        const res = await GET(req());

        expect(res.status).toBe(502);
        await expect(res.json()).resolves.toEqual({error: 'Failed to fetch daily picks'});
    });
});
