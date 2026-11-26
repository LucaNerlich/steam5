import {describe, expect, it} from 'vitest';
import {LEADERBOARD_ROUTES} from '@/lib/leaderboardRoutes';
import {Routes} from '../../app/routes';

describe('LEADERBOARD_ROUTES', () => {
    it('derives every tab from the Routes constants', () => {
        // Renaming the leaderboard root used to leave two hardcoded literals behind, which
        // silently produced two active tabs.
        expect(LEADERBOARD_ROUTES.map((route) => route.href)).toContain(Routes.leaderboard);
        for (const route of LEADERBOARD_ROUTES) {
            expect(route.href.startsWith(Routes.leaderboard)).toBe(true);
        }
    });
});
