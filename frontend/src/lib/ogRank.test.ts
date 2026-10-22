import {describe, expect, it} from 'vitest';
import {profileRank} from './ogRank';

describe('profileRank', () => {
    it('returns no percentile or badge when the leaderboard is missing', () => {
        expect(profileRank(null, 500, 4.2)).toEqual({totalPct: null, avgPct: null, badge: null});
    });

    it('returns no percentile or badge when the leaderboard is empty', () => {
        expect(profileRank([], 500, 4.2)).toEqual({totalPct: null, avgPct: null, badge: null});
    });

    it('ranks against a populated leaderboard and returns the badge', () => {
        const leaders = [{totalPoints: 100, avgPoints: 5}, {totalPoints: 50, avgPoints: 3}];

        const rank = profileRank(leaders, 100, 5);

        expect(rank.totalPct).toBe(1);
        expect(rank.avgPct).toBe(1);
        expect(rank.badge?.label).toBe('S+');
    });
});
