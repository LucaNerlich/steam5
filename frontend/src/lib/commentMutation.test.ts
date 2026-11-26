import {afterEach, describe, expect, it, vi} from 'vitest';
import {NextRequest} from 'next/server';

const cookieGet = vi.fn(() => undefined);
vi.mock('next/headers', () => ({cookies: async () => ({get: cookieGet})}));

import {proxyCommentMutation} from './commentMutation';

afterEach(() => {
    vi.restoreAllMocks();
});

describe('proxyCommentMutation missing-auth log', () => {
    it('sanitizes the untrusted comment id', async () => {
        // A %0A segment used to reach the log raw and forge extra lines.
        const logged: unknown[] = [];
        vi.spyOn(console, 'error').mockImplementation((...args: unknown[]) => {
            logged.push(args[1]);
        });
        const req = new NextRequest('https://steam5.org/api/x', {
            method: 'POST',
            headers: {origin: 'https://steam5.org'},
        });

        const res = await proxyCommentMutation(req, {
            route: 'comments/test',
            path: '/api/x',
            commentId: '7\nFAKE-LOG-LINE',
            errorMessage: 'failed',
        });

        expect(res.status).toBe(401);
        // Assert the value itself: JSON.stringify escapes the newline, so a stringified
        // check would pass with the unsanitized id.
        expect(logged[0]).toEqual({commentId: '7FAKE-LOG-LINE'});
    });
});
