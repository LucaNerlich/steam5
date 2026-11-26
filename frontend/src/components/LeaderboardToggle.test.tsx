import {describe, expect, it, vi} from 'vitest';
import {renderToStaticMarkup} from 'react-dom/server';

let pathname = '/review-guesser/leaderboard';
vi.mock('next/navigation', () => ({usePathname: () => pathname}));

import LeaderboardToggle from './LeaderboardToggle';

const ROUTES = [
    '/review-guesser/leaderboard/today',
    '/review-guesser/leaderboard/weekly',
    '/review-guesser/leaderboard/season',
    '/review-guesser/leaderboard',
    '/review-guesser/leaderboard/perfect-days',
    '/review-guesser/leaderboard/hardest',
];

describe('LeaderboardToggle', () => {
    it('marks exactly the active tab with aria-current="page"', () => {
        // The active route was CSS-only, so all six tabs sounded identical to a screen reader.
        for (const route of ROUTES) {
            pathname = route;
            const html = renderToStaticMarkup(<LeaderboardToggle/>);
            const matches = html.match(/aria-current="page"/g) ?? [];
            expect(matches, route).toHaveLength(1);
            // Wrong-tab marking must fail too: match the anchor carrying the marker.
            const tag = html.match(/<a[^>]*aria-current="page"[^>]*>/)?.[0] ?? '';
            expect(tag, route).toContain(`href="${route}"`);
        }
    });
});
