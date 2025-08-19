"use client";

import "@/styles/components/leaderboard.css";
import Image from "next/image";
import useSWR from "swr";

export type LeaderEntry = {
    steamId: string;
    personaName: string;
    totalPoints: number;
    rounds: number;
    hits: number;
    tooHigh: number;
    tooLow: number;
    avgPoints: number;
    avatar?: string | null;
    avatarBlurdata?: string | null;
    profileUrl?: string | null;
};

const fetcher = (url: string) => fetch(url, {headers: {accept: 'application/json'}}).then(r => {
    if (!r.ok) throw new Error(`Failed to load ${url}: ${r.status}`);
    return r.json();
});

export default function LeaderboardTable(props: { mode: 'today' | 'all'; refreshMs?: number; ariaLabel?: string }) {
    const backend = process.env.NEXT_PUBLIC_API_DOMAIN || 'http://localhost:8080';
    const endpoint = props.mode === 'today' ? '/api/leaderboard/today' : '/api/leaderboard/all';
    const {data, error, isLoading} = useSWR<LeaderEntry[]>(`${backend}${endpoint}`, fetcher, {
        refreshInterval: props.refreshMs ?? (props.mode === 'today' ? 5000 : 10000),
        revalidateOnFocus: true,
    });

    if (error) return <p className="text-muted">Failed to load leaderboard. Please try again.</p>;
    if (isLoading || !data) return <p className="text-muted">Loading leaderboard…</p>;

    const entries = data;
    const aria = props.ariaLabel ?? (props.mode === 'today' ? 'Today Leaderboard' : 'All-time Leaderboard');

    return (
        <div className="leaderboard">
            <table className="leaderboard__table" aria-label={aria}>
                <thead>
                <tr>
                    <th scope="col" className="num">#</th>
                    <th scope="col">Player</th>
                    <th scope="col" className="num">Points</th>
                    <th scope="col" className="num">Rounds</th>
                    <th scope="col" className="num">Hits</th>
                    <th scope="col" className="num">Too High</th>
                    <th scope="col" className="num">Too Low</th>
                    <th scope="col" className="num">Avg</th>
                </tr>
                </thead>
                <tbody>
                {entries.map((entry, i) => (
                    <tr key={entry.steamId}>
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
                                        <strong>{entry.personaName || entry.steamId}</strong>
                                    </a>
                                ) : (
                                    <strong>{entry.personaName || entry.steamId}</strong>
                                )}
                            </div>
                        </td>
                        <td className="num">{entry.totalPoints}</td>
                        <td className="num">{entry.rounds}</td>
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


