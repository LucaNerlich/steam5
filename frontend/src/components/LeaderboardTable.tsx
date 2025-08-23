"use client";

import "@/styles/components/leaderboard.css";
import Image from "next/image";
import useSWR from "swr";

export type LeaderEntry = {
    personaName: string;
    totalPoints: number;
    rounds: number;
    hits: number;
    tooHigh: number;
    tooLow: number;
    avgPoints: number;
    streak: number;
    avatar?: string | null;
    avatarBlurdata?: string | null;
    profileUrl?: string | null;
};

const fetcher = (url: string) => fetch(url, {headers: {accept: 'application/json'}}).then(r => {
    if (!r.ok) throw new Error(`Failed to load ${url}: ${r.status}`);
    return r.json();
});

export default function LeaderboardTable(props: {
    mode: 'today' | 'weekly' | 'weekly-floating' | 'all';
    refreshMs?: number;
    ariaLabel?: string
}) {
    const backend = process.env.NEXT_PUBLIC_API_DOMAIN || 'http://localhost:8080';

    let aria;
    let endpoint;
    switch (props.mode) {
        case 'today':
            endpoint = '/api/leaderboard/today';
            aria = props.ariaLabel ?? 'Today Leaderboard';
            break;
        case 'weekly':
            endpoint = '/api/leaderboard/weekly';
            aria = props.ariaLabel ?? 'Weekly Leaderboard';
            break;
        case 'weekly-floating':
            endpoint = '/api/leaderboard/weekly?floating=true';
            aria = props.ariaLabel ?? 'Weekly Leaderboard';
            break;
        case 'all':
        default:
            endpoint = '/api/leaderboard/all';
            aria = props.ariaLabel ?? 'All-time Leaderboard';

    }
    const {data, error, isLoading} = useSWR<LeaderEntry[]>(`${backend}${endpoint}`, fetcher, {
        refreshInterval: props.refreshMs ?? (props.mode === 'today' ? 5000 : 10000),
        revalidateOnFocus: true,
    });

    if (error) return <p className="text-muted">Failed to load leaderboard. Please try again.</p>;
    if (isLoading || !data) return <p className="text-muted">Loading leaderboard…</p>;

    return (
        <div className="leaderboard">
            <table className="leaderboard__table" aria-label={aria}>
                <thead>
                <tr>
                    <th scope="col" className="num">#</th>
                    <th scope="col">Player</th>
                    <th scope="col" className="num">Points</th>
                    <th scope="col" className="num">Rounds</th>
                    <th scope="col" className="num" title='Uninterrupted daily-challenges'>Streak</th>
                    <th scope="col" className="num" title='Correct guess'>Hits</th>
                    <th scope="col" className="num">Too High</th>
                    <th scope="col" className="num">Too Low</th>
                    <th scope="col" className="num">Avg</th>
                </tr>
                </thead>
                <tbody>
                {data.map((entry, i) => (
                    <tr key={entry.profileUrl}>
                        <td>{i + 1}</td>
                        <td>
                            <div className="leaderboard__player">
                                {entry.avatar && (
                                    <div className="leaderboard__avatar-wrap"
                                         style={{backgroundImage: entry.avatarBlurdata ? `url(${entry.avatarBlurdata})` : undefined}}>
                                        <Image className="leaderboard__avatar"
                                               src={entry.avatar}
                                               placeholder={'empty'}
                                               alt=""
                                               width={24}
                                               height={24}/>
                                    </div>
                                )}
                                {entry.profileUrl ? (
                                    <a href={entry.profileUrl} target="_blank" rel="noopener noreferrer"
                                       className="leaderboard__profile-link">
                                        <strong>{entry.personaName || 'no-name'}</strong>
                                    </a>
                                ) : (
                                    <strong>{entry.personaName || 'no-name'}</strong>
                                )}
                            </div>
                        </td>
                        <td className="num">{entry.totalPoints}</td>
                        <td className="num">{entry.rounds}</td>
                        <td className="num">{entry.streak}</td>
                        <td className="num">{entry.hits}</td>
                        <td className="num">{entry.tooHigh}</td>
                        <td className="num">{entry.tooLow}</td>
                        <td className="num">{entry.avgPoints.toFixed(2)}</td>
                    </tr>
                ))}
                </tbody>
            </table>
        </div>
    );
}


