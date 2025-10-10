'use client';

import {useEffect, useState} from 'react';
import {scoreForRound} from '@/lib/scoring';
import '@/styles/components/archive-summary.css';

interface AlwaysPickScore {
    bucketIndex: number;
    bucketLabel: string;
    avgPoints: number;
    rounds: number;
}

interface HistoricalAlwaysPickResponse {
    from: string;
    to: string;
    scores: AlwaysPickScore[];
}

interface MyGuessDto {
    roundIndex: number;
    appId: number;
    selectedBucket: string;
    actualBucket: string;
    totalReviews: number;
}

interface ArchiveSummaryProps {
    date?: string; // If undefined, shows all-time stats
    bucketLabels: string[];
    bucketTitles: string[];
}

export default function ArchiveSummary({date, bucketLabels, bucketTitles}: ArchiveSummaryProps) {
    const [baseline, setBaseline] = useState<HistoricalAlwaysPickResponse | null>(null);
    const [myGuesses, setMyGuesses] = useState<MyGuessDto[] | null>(null);
    const [isSignedIn, setIsSignedIn] = useState(false);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        const loadData = async () => {
            setLoading(true);
            try {
                // Load baseline
                const backend = process.env.NEXT_PUBLIC_API_DOMAIN || 'http://localhost:8080';
                const baselineUrl = date
                    ? `${backend}/api/review-game/history/always-pick?from=${encodeURIComponent(date)}&to=${encodeURIComponent(date)}`
                    : `${backend}/api/review-game/history/always-pick`;
                const baselineRes = await fetch(baselineUrl, {headers: {'accept': 'application/json'}});
                if (baselineRes.ok) {
                    const baselineData: HistoricalAlwaysPickResponse = await baselineRes.json();
                    setBaseline(baselineData);
                }

                // Check if signed in and load personal guesses
                const myUrl = date
                    ? `/api/review-game/my/day/${encodeURIComponent(date)}`
                    : `/api/review-game/my/history`;
                const myRes = await fetch(myUrl);
                if (myRes.ok) {
                    const myData: MyGuessDto[] = await myRes.json();
                    setMyGuesses(myData);
                    setIsSignedIn(true);
                } else if (myRes.status === 401) {
                    setIsSignedIn(false);
                }
            } catch (e) {
                console.error('Failed to load archive summary data', e);
            } finally {
                setLoading(false);
            }
        };
        loadData();
    }, [date]);

    if (loading || !baseline) {
        return null;
    }

    const rounds = baseline.scores[0]?.rounds || 0;
    if (rounds === 0) {
        return null;
    }

    // Compute user's total if signed in
    const hasUserData = isSignedIn && myGuesses && myGuesses.length > 0;
    let userTotal = 0;
    if (hasUserData) {
        userTotal = myGuesses.reduce((sum, guess) => {
            const result = scoreForRound(bucketLabels, guess.selectedBucket, guess.actualBucket);
            return sum + result.points;
        }, 0);
    }

    // Compute random strategy average
    // Expected value when picking randomly: average of all bucket scores
    const randomAvg = baseline.scores.reduce((sum, s) => sum + s.avgPoints, 0) / baseline.scores.length;
    const randomTotal = Math.round(randomAvg * rounds);

    // Build table rows
    interface TableRow {
        key: string;
        strategy: string;
        totalPoints: number;
        avgPoints: number;
        delta: number | null;
    }

    const rows: TableRow[] = baseline.scores.map((score, idx) => {
        const totalPoints = Math.round(score.avgPoints * rounds);
        const bucketTitle = bucketTitles[idx] || score.bucketLabel;
        const delta = hasUserData ? userTotal - totalPoints : null;
        return {
            key: `bucket-${score.bucketIndex}`,
            strategy: bucketTitle,
            totalPoints,
            avgPoints: score.avgPoints,
            delta
        };
    });

    // Add random row
    const randomDelta = hasUserData ? userTotal - randomTotal : null;
    rows.push({
        key: 'random',
        strategy: 'Random',
        totalPoints: randomTotal,
        avgPoints: randomAvg,
        delta: randomDelta
    });

    return (
        <section className="archive-summary">
            <h2 className="archive-summary__title">Strategy Overview</h2>
            <p className="archive-summary__description">
                Points you would have earned by {date ? 'always picking the same bucket' : 'following each strategy across all archive rounds'}:
            </p>
            <div className="archive-summary__table-wrapper">
                <table className="archive-summary__table">
                    <thead>
                    <tr>
                        <th>Strategy</th>
                        <th>Total Points</th>
                        <th>Avg per Round</th>
                        {hasUserData && (
                            <th>Your Delta</th>
                        )}
                    </tr>
                    </thead>
                    <tbody>
                    {rows.map((row) => (
                        <tr key={row.key} className={row.key === 'random' ? 'archive-summary__random-row' : ''}>
                            <td className="archive-summary__bucket-title">{row.strategy}</td>
                            <td className="archive-summary__total">{row.totalPoints}</td>
                            <td className="archive-summary__avg">{row.avgPoints.toFixed(2)}</td>
                            {hasUserData && (
                                <td className={`archive-summary__delta ${row.delta && row.delta > 0 ? 'positive' : row.delta && row.delta < 0 ? 'negative' : 'neutral'}`}>
                                    {row.delta && row.delta > 0 ? '+' : ''}{row.delta}
                                </td>
                            )}
                        </tr>
                    ))}
                    </tbody>
                </table>
            </div>
            {hasUserData && (
                <p className="archive-summary__user-total">
                    Your total: <strong>{userTotal}</strong> points ({myGuesses!.length} rounds)
                </p>
            )}
        </section>
    );
}

