import {readFileSync} from 'node:fs';
import {describe, expect, it} from 'vitest';

// Segment-level `revalidate` on a GET handler opts the whole route into static
// prerendering: next build runs the handler and caches whatever upstream returned,
// including an error. The proxy helper's `revalidate` option is the supported way.
const CACHED_GET_ROUTES = [
    'app/api/leaderboard/all/route.ts',
    'app/api/leaderboard/season/route.ts',
    'app/api/leaderboard/today/route.ts',
    'app/api/leaderboard/weekly/route.ts',
    'app/api/leaderboard/achievements/route.ts',
    'app/api/review-game/today/route.ts',
    'app/api/review-game/day/summary/[date]/route.ts',
    'app/api/stats/game/hardest/route.ts',
    'app/api/stats/game/perfect-days/route.ts',
];

describe('cached GET proxies', () => {
    it.each(CACHED_GET_ROUTES)('%s is not statically prerendered', (file) => {
        expect(readFileSync(file, 'utf8')).not.toMatch(/export const revalidate\s*=/);
    });
});
