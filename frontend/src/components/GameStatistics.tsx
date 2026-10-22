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

const jsonFetcher = (url: string) => fetch(url, {headers: {'accept': 'application/json'}}).then(r => {
    if (!r.ok) return null;
    return r.json();
});

export default function GameStatistics() {
    const {data: stats, isLoading: statsLoading} = useSWR<GameStatisticsData | null>(
        '/api/stats/game', jsonFetcher, {revalidateOnFocus: false}
    );
    const {data: picksData, isLoading: picksLoading} = useSWR<{totalPicks: number} | null>(
        `${BACKEND_ORIGIN}/api/metrics/picks/summary`, jsonFetcher, {revalidateOnFocus: false}
    );
    const totalRounds = picksData?.totalPicks ?? null;
    const loading = statsLoading || picksLoading;

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
                            const archiveUrl = game.pickDate 
                                ? `/review-guesser/archive/${game.pickDate}${game.roundIndex ? `#round-${game.roundIndex}` : ''}`
                                : null;
                            return (
                                <li key={game.appId}>
                                    <Link href={`https://store.steampowered.com/app/${game.appId}`} target="_blank" rel="noopener noreferrer">
                                        {game.name}
                                    </Link>
                                    {' '}
                                    <span className="text-muted">({game.totalReviews.toLocaleString()} reviews)</span>
                                    {archiveUrl && (
                                        <>
                                            {' '}
                                            <Link href={archiveUrl} className="archive-link">
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
                            <Link href={`/review-guesser/archive/${stats.dailyAvgScores.highest.date}`}>
                                {formatDate(stats.dailyAvgScores.highest.date)}
                            </Link>
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
                            <Link href={`/review-guesser/archive/${stats.dailyAvgScores.lowest.date}`}>
                                {formatDate(stats.dailyAvgScores.lowest.date)}
                            </Link>
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

