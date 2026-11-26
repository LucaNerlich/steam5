'use client';

import {formatDate} from '@/lib/format';
import Link from 'next/link';
import useSWR from 'swr';
import '@/styles/components/game-statistics.css';
import {BACKEND_ORIGIN} from '@/lib/backend';

interface TopGameByReviews {
    appId: number;
    name: string;
    totalReviews: number;
    pickDate: string | null;
    roundIndex: number | null;
}

interface DailyAvgScore {
    date: string;
    avgScore: number;
    playerCount: number;
}

interface DailyAvgScoreStats {
    highest: DailyAvgScore | null;
    lowest: DailyAvgScore | null;
}

interface GameStatisticsData {
    topGamesByReviewCount: TopGameByReviews[];
    dailyAvgScores: DailyAvgScoreStats;
}

// Stats are the section's own content: a failure must be visible, not render as "no data".
const jsonFetcher = async (url: string) => {
    const r = await fetch(url, {headers: {'accept': 'application/json'}});
    if (!r.ok) throw new Error(`Failed to load ${url} (${r.status})`);
    return r.json();
};
// The total-rounds line is decoration; its failure must not hide the section.
const optionalFetcher = (url: string) => fetch(url, {headers: {'accept': 'application/json'}})
    .then(r => (r.ok ? r.json() : null))
    .catch(() => null);

export default function GameStatistics() {
    const {data: stats, isLoading: statsLoading, error: statsError} = useSWR<GameStatisticsData>(
        '/api/stats/game', jsonFetcher, {revalidateOnFocus: false}
    );
    const {data: picksData, isLoading: picksLoading} = useSWR<{totalPicks: number} | null>(
        `${BACKEND_ORIGIN}/api/metrics/picks/summary`, optionalFetcher, {revalidateOnFocus: false}
    );
    const totalRounds = picksData?.totalPicks ?? null;
    const loading = statsLoading || picksLoading;
    // The archive route 404s today's date, so never link a day that has not ended;
    // /stats/game derives these dates from picks and can return today's.
    const today = new Date().toISOString().slice(0, 10);
    const archiveHref = (date: string | null | undefined) =>
        date && date < today ? `/review-guesser/archive/${date}` : null;

    if (statsError && !stats) {
        return (
            <section className="game-statistics">
                <h2>Game Statistics</h2>
                <p className="text-muted">Game statistics are unavailable right now.</p>
            </section>
        );
    }

    if (loading) {
        return (
            <section className="game-statistics">
                <h2>Game Statistics</h2>
                <p className="text-muted">Loading...</p>
            </section>
        );
    }

    if (!stats) {
        return null;
    }

    return (
        <section className="game-statistics">
            <h2>Game Statistics</h2>
            <dl>
                {totalRounds !== null && (
                    <>
                        <dt>Total Rounds Played</dt>
                        <dd>{totalRounds.toLocaleString()}</dd>
                    </>
                )}
                <dt>Top 10 Games by Review Count</dt>
                <dd>
                    <ol>
                        {stats.topGamesByReviewCount.map((game) => {
                            const archiveUrl = archiveHref(game.pickDate);
                            const archiveLink = archiveUrl
                                ? `${archiveUrl}${game.roundIndex ? `#round-${game.roundIndex}` : ''}`
                                : null;
                            return (
                                <li key={game.appId}>
                                    <Link href={`https://store.steampowered.com/app/${game.appId}`} target="_blank" rel="noopener noreferrer">
                                        {game.name}
                                    </Link>
                                    {' '}
                                    <span className="text-muted">({game.totalReviews.toLocaleString()} reviews)</span>
                                    {archiveLink && (
                                        <>
                                            {' '}
                                            <Link href={archiveLink} className="archive-link">
                                                archive
                                            </Link>
                                        </>
                                    )}
                                </li>
                            );
                        })}
                    </ol>
                </dd>
                {stats.dailyAvgScores.highest && (
                    <>
                        <dt>Day with Highest Average User Score</dt>
                        <dd>
                            {archiveHref(stats.dailyAvgScores.highest.date) ? (
                                <Link href={archiveHref(stats.dailyAvgScores.highest.date)!}>
                                    {formatDate(stats.dailyAvgScores.highest.date)}
                                </Link>
                            ) : formatDate(stats.dailyAvgScores.highest.date)}
                            {' '}
                            <span className="text-muted">
                                (avg: {stats.dailyAvgScores.highest.avgScore.toFixed(2)} points, {stats.dailyAvgScores.highest.playerCount} players)
                            </span>
                        </dd>
                    </>
                )}
                {stats.dailyAvgScores.lowest && (
                    <>
                        <dt>Day with Lowest Average User Score</dt>
                        <dd>
                            {archiveHref(stats.dailyAvgScores.lowest.date) ? (
                                <Link href={archiveHref(stats.dailyAvgScores.lowest.date)!}>
                                    {formatDate(stats.dailyAvgScores.lowest.date)}
                                </Link>
                            ) : formatDate(stats.dailyAvgScores.lowest.date)}
                            {' '}
                            <span className="text-muted">
                                (avg: {stats.dailyAvgScores.lowest.avgScore.toFixed(2)} points, {stats.dailyAvgScores.lowest.playerCount} players)
                            </span>
                        </dd>
                    </>
                )}
            </dl>
        </section>
    );
}

