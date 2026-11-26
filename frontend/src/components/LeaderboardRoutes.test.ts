import {describe, expect, it} from 'vitest';
import {LEADERBOARD_ROUTES} from '@/lib/leaderboardRoutes';
import {Routes} from '../../app/routes';

describe('LEADERBOARD_ROUTES', () => {
    it('derives every tab from the Routes constants', () => {
        // Pin the exact list: a hardcoded literal beside the constant is the #729 regression.
        const hrefs = LEADERBOARD_ROUTES.map((route) => route.href);
        for (const href of [
            Routes.leaderboardToday,
            Routes.leaderboardWeekly,
            Routes.leaderboardSeason,
            Routes.leaderboard,
            Routes.leaderboardPerfectDays,
            Routes.leaderboardHardest,
        ]) {
            expect(hrefs).toContain(href);
        }
        expect(new Set(hrefs).size).toBe(hrefs.length);
    });
});
