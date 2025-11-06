'use client';

import {useEffect, useState} from 'react';
import {formatDate} from '@/lib/format';
import Link from 'next/link';
import '@/styles/components/game-statistics.css';

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

interface GameStatistics {
    topGamesByReviewCount: TopGameByReviews[];
    dailyAvgScores: DailyAvgScoreStats;
}

export default function GameStatistics() {
    const [stats, setStats] = useState<GameStatistics | null>(null);
    const [loading, setLoading] = useState(true);
    const [totalRounds, setTotalRounds] = useState<number | null>(null);

    useEffect(() => {
        const loadData = async () => {
            setLoading(true);
            try {
                // Load game statistics
                const statsRes = await fetch('/api/stats/game');
                if (statsRes.ok) {
                    const statsData: GameStatistics = await statsRes.json();
                    console.log('Game stats data:', statsData);
                    setStats(statsData);
                }

                // Load total rounds from picks summary
                const backend = process.env.NEXT_PUBLIC_API_DOMAIN || 'http://localhost:8080';
                const picksRes = await fetch(`${backend}/api/metrics/picks/summary`, {
                    headers: {'accept': 'application/json'},
                });
                if (picksRes.ok) {
                    const picksData: {totalPicks: number} = await picksRes.json();
                    setTotalRounds(picksData.totalPicks);
                }
            } catch (e) {
                console.error('Failed to load game statistics', e);
            } finally {
                setLoading(false);
            }
        };
        loadData();
    }, []);

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
                            console.log('Game:', game.name, 'pickDate:', game.pickDate, 'roundIndex:', game.roundIndex, 'archiveUrl:', archiveUrl);
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

