import {beforeEach, describe, expect, it, vi} from 'vitest';
import {renderToStaticMarkup} from 'react-dom/server';
import React from 'react';
import useSWR from 'swr';
import LeaderboardTable from './LeaderboardTable';

vi.mock('swr', () => ({default: vi.fn()}));
vi.mock('@/components/Avatar', () => ({default: () => null}));

const row = {
    steamId: 'u1', personaName: 'Alice', avatar: null, profileUrl: null,
    totalPoints: 5, rounds: 1, hits: 1, flops: 0, tooHigh: 0, tooLow: 0,
    avgPoints: 5, streak: 1, achievements: [],
} as never;

describe('LeaderboardTable error handling', () => {
    beforeEach(() => vi.mocked(useSWR).mockReset());

    it('keeps the last table when a revalidation fails', () => {
        // SWR keeps the previous payload on a failed revalidation; the table must not replace it.
        vi.mocked(useSWR).mockReturnValue({data: {data: [row], refreshedAt: null}, error: new Error('502'), isLoading: false} as never);

        const html = renderToStaticMarkup(<LeaderboardTable mode="today"/>);

        expect(html).toContain('Alice');
        expect(html).not.toContain('Failed to load leaderboard');
    });

    it('shows the error when there is nothing to render', () => {
        vi.mocked(useSWR).mockReturnValue({data: undefined, error: new Error('502'), isLoading: false} as never);

        const html = renderToStaticMarkup(<LeaderboardTable mode="today"/>);

        expect(html).toContain('Failed to load leaderboard');
    });
});
