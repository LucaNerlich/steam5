"use client";

import "@/styles/components/leaderboard.css";
import Link from "next/link";
import useSWR from "swr";
import {formatDeception, formatMostMissed, HardestGame} from "@/lib/hardestGames";
import {formatRefreshedAt} from "@/lib/leaderboard";

const ENDPOINT = "/api/stats/game/hardest";

type HardestGamesFetchResult = { data: HardestGame[]; refreshedAt: string | null };

const fetcher = async (url: string): Promise<HardestGamesFetchResult> => {
    const r = await fetch(url, {
        headers: {accept: "application/json"},
        cache: "no-cache",
    });
    if (!r.ok) throw new Error(`Failed to load ${url}: ${r.status}`);
    const data = await r.json();
    return {data, refreshedAt: r.headers.get('X-Leaderboard-Refreshed-At')};
};

export default function HardestGamesTable(props: {
    initialData?: HardestGame[] | null;
    refreshMs?: number;
}) {
    const refreshInterval = props.refreshMs ?? 3600000;

    const {data: hardestGamesResult, error, isLoading} = useSWR<HardestGamesFetchResult>(ENDPOINT, fetcher, {
        refreshInterval,
        revalidateOnFocus: true,
        focusThrottleInterval: refreshInterval,
        fallbackData: props.initialData ? {data: props.initialData, refreshedAt: null} : undefined,
    });

    const data = hardestGamesResult?.data;
    const refreshedAt = hardestGamesResult?.refreshedAt ?? null;
    const lastUpdatedText = formatRefreshedAt(refreshedAt);

    if (error && !data) {
        return <p className="text-muted">Failed to load hardest games. Please try again soon.</p>;
    }
    if (isLoading && !props.initialData) {
        return <p className="text-muted">Loading hardest games…</p>;
    }
    if (!data) {
        return <p className="text-muted">Loading hardest games…</p>;
    }
    if (data.length === 0) {
        return <p className="text-muted">No games available yet. Check back once more rounds have been played.</p>;
    }

    return (
        <div className="leaderboard">
            <div className="leaderboard__scroll">
                <table className="leaderboard__table" aria-label="Hardest games ranked by lowest average score">
                    <thead>
                    <tr>
                        <th scope="col" className="num" title="Rank by difficulty (1 = hardest)">#</th>
                        <th scope="col" style={{maxWidth: '220px'}} title="Steam game name with links to the Steam store and the archive page for the latest pick date">Game</th>
                        <th scope="col" className="num" title="Average points scored by all players on this game (0–5 scale)">Avg Score</th>
                        <th scope="col" className="num" title="Number of unique players who guessed this game">Players</th>
                        <th scope="col" title="Whether players consistently over-guessed (🔺) or under-guessed (🔻) the review count, and what percentage guessed in that wrong direction">Deception</th>
                        <th scope="col" title="The review-count bucket most frequently chosen when players guessed wrong (and how many times), plus the correct answer">Most Missed</th>
                    </tr>
                    </thead>
                    <tbody>
                    {data.map((game, i) => (
                        <tr key={game.appId}>
                            <td className="num">{i + 1}</td>
                            <td>
                                <span
                                    className="leaderboard__profile-link"
                                    style={{
                                        display: 'block',
                                        maxWidth: '220px',
                                        overflow: 'hidden',
                                        textOverflow: 'ellipsis',
                                        whiteSpace: 'nowrap'
                                    }}
                                    title={game.appName}
                                >
                                    {game.appName}
                                </span>
                                <span className="hardest__links">
                                    <a
                                        href={`https://store.steampowered.com/app/${game.appId}`}
                                        target="_blank"
                                        rel="noopener noreferrer"
                                    >
                                        Steam ↗
                                    </a>
                                    {" · "}
                                    <Link href={`/review-guesser/archive/${game.latestPickDate}`}>
                                        Archive
                                    </Link>
                                </span>
                            </td>
                            <td className="num">{game.avgScore.toFixed(1)} / 5</td>
                            <td className="num">{game.playerCount}</td>
                            <td>{formatDeception(game)}</td>
                            <td>{formatMostMissed(game)}</td>
                        </tr>
                    ))}
                    </tbody>
                </table>
            </div>
            {lastUpdatedText && (
                <p className="text-muted leaderboard__last-updated">
                    Last updated: {lastUpdatedText}
                </p>
            )}
        </div>
    );
}
