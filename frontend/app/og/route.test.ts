import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest';
import {GET} from './route';

/**
 * The /og card made it to production answering 500 on every request only because
 * nothing exercised it in CI. This smoke check pins the default-variant happy path
 * end to end: the route parses the query offline (no backend fetch) and returns a
 * real rendered PNG.
 *
 * Pulling the body is the load-bearing part: `next/og`'s `ImageResponse` builds a
 * lazily-rendered stream, so status and content-type are populated even when the
 * render itself is broken. Consuming the bytes is what makes satori/font-pipeline
 * drift reject here instead of passing vacuously.
 */
describe('GET /og', () => {
    const realFetch = globalThis.fetch.bind(globalThis);
    let backendCalls: string[] = [];

    beforeEach(() => {
        backendCalls = [];
        vi.stubEnv('NEXT_PUBLIC_API_DOMAIN', 'http://og-default-test.invalid');
        // The renderer itself calls `fetch` for the embedded `data:` image, so only
        // non-data URLs are treated as backend traffic: record them and fail the test.
        vi.stubGlobal('fetch', vi.fn(async (input: string | URL | Request) => {
            const url = typeof input === 'string' ? input : input instanceof URL ? input.href : input.url;
            if (url.startsWith('data:')) return realFetch(url);
            backendCalls.push(url);
            throw new Error(`/og unexpectedly fetched ${url}`);
        }));
    });

    afterEach(() => {
        vi.unstubAllEnvs();
        vi.unstubAllGlobals();
    });

    it('serves a rendered PNG card without touching the backend', async () => {
        const response = await GET(new Request('http://localhost:3000/og'));

        expect(response.status).toBe(200);
        expect(response.headers.get('content-type')).toBe('image/png');
        expect(backendCalls).toEqual([]);

        const bytes = new Uint8Array(await response.arrayBuffer());

        expect(bytes.length).toBeGreaterThan(1000);
        // `\x89PNG\r\n\x1a\n` — the body is an actual image, not an empty or failed stream.
        expect(Array.from(bytes.slice(0, 8))).toEqual([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);
    });
});
