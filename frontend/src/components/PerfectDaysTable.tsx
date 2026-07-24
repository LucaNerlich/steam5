"use client";

import "@/styles/components/leaderboard.css";
import Image from "next/image";
import Link from "next/link";
import useSWR from "swr";
import {useMemo} from "react";
import {PerfectDay} from "@/lib/perfectDays";
import {formatRefreshedAt} from "@/lib/leaderboard";

const ENDPOINT = "/api/stats/game/perfect-days";

type PerfectDaysFetchResult = { data: PerfectDay[]; refreshedAt: string | null };

const fetcher = async (url: string): Promise<PerfectDaysFetchResult> => {
    const r = await fetch(url, {
        headers: {accept: "application/json"},
        cache: "no-cache",
    });
    if (!r.ok) throw new Error(`Failed to load ${url}: ${r.status}`);
    const data = await r.json();
    return {data, refreshedAt: r.headers.get('X-Leaderboard-Refreshed-At')};
};

export default function PerfectDaysTable(props: {
    initialData?: PerfectDay[] | null;
    refreshMs?: number;
}) {
    const refreshInterval = props.refreshMs ?? 3600000;

    const {data: perfectDaysResult, error, isLoading} = useSWR<PerfectDaysFetchResult>(ENDPOINT, fetcher, {
        refreshInterval,
        revalidateOnFocus: true,
        focusThrottleInterval: refreshInterval,
        fallbackData: props.initialData ? {data: props.initialData, refreshedAt: null} : undefined,
    });

    const data = perfectDaysResult?.data;
    const refreshedAt = perfectDaysResult?.refreshedAt ?? null;
    const lastUpdatedText = formatRefreshedAt(refreshedAt);

    const playerCounts = useMemo(() => {
        const counts = new Map<string, { steamId: string; name: string; avatar?: string | null; avatarBlurdata?: string | null; count: number }>();
        for (const entry of data ?? []) {
            const key = entry.steamId;
            const existing = counts.get(key);
            if (existing) {
                existing.count++;
            } else {
                counts.set(key, {steamId: entry.steamId, name: entry.personaName, avatar: entry.avatar, avatarBlurdata: entry.avatarBlurdata, count: 1});
            }
        }
        return [...counts.values()].sort((a, b) => b.count - a.count);
    }, [data]);

    if (error && !data) {
        return <p className="text-muted">Failed to load perfect days. Please try again soon.</p>;
    }
    if (isLoading && !props.initialData) {
        return <p className="text-muted">Loading perfect days…</p>;
    }
    if (!data) {
        return <p className="text-muted">Loading perfect days…</p>;
    }
    if (data.length === 0) {
        return <p className="text-muted">No perfect days yet. Be the first!</p>;
    }

    return (
        <div className="leaderboard">
            <div className="leaderboard__scroll">
                <table className="leaderboard__table" aria-label="Perfect days — players who scored 25 points">
                    <thead>
                    <tr>
                        <th scope="col" className="num" title="Rank">#</th>
                        <th scope="col" title="Player">Player</th>
                        <th scope="col" title="Date of the perfect day">Date</th>
                        <th scope="col" title="Games played that day">Games</th>
                        <th scope="col" title="Link to the archive page for this date">Archive</th>
                    </tr>
                    </thead>
                    <tbody>
                    {data.map((entry, i) => (
                        <tr key={`${entry.steamId}-${entry.gameDate}`}>
                            <td className="num">{i + 1}</td>
                            <td>
                                <span className="leaderboard__profile-link">
                                    {entry.avatar && (
                                        <div className="leaderboard__avatar-wrap">
                                            <Image
                                                className="leaderboard__avatar"
                                                src={entry.avatar}
                                                alt={`${entry.personaName}'s avatar`}
                                                width={24}
                                                height={24}
                                                placeholder={entry.avatarBlurdata ? 'blur' : 'empty'}
                                                blurDataURL={entry.avatarBlurdata || undefined}
                                            />
                                        </div>
                                    )}
                                    {entry.personaName}
                                </span>
                            </td>
                            <td>{entry.gameDate}</td>
                            <td style={{maxWidth: 300, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap'}} title={entry.appNames.join(', ')}>
                                {entry.appNames.join(', ')}
                            </td>
                            <td>
                                <Link href={`/review-guesser/archive/${entry.gameDate}`}>
                                    View
                                </Link>
                            </td>
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

            <h2 className="leaderboard__subtitle">Player Rankings</h2>
            <div className="leaderboard__scroll">
                <table className="leaderboard__table" aria-label="Players ranked by number of perfect days">
                    <thead>
                    <tr>
                        <th scope="col" className="num" title="Rank">#</th>
                        <th scope="col" title="Player">Player</th>
                        <th scope="col" className="num" title="Number of perfect days">Perfect Days</th>
                    </tr>
                    </thead>
                    <tbody>
                    {playerCounts.map((player, i) => (
                        <tr key={player.steamId}>
                            <td className="num">{i + 1}</td>
                            <td>
                                <span className="leaderboard__profile-link">
                                    {player.avatar && (
                                        <div className="leaderboard__avatar-wrap">
                                            <Image
                                                className="leaderboard__avatar"
                                                src={player.avatar}
                                                alt={`${player.name}'s avatar`}
                                                width={24}
                                                height={24}
                                                placeholder={player.avatarBlurdata ? 'blur' : 'empty'}
                                                blurDataURL={player.avatarBlurdata || undefined}
                                            />
                                        </div>
                                    )}
                                    {player.name}
                                </span>
                            </td>
                            <td className="num">{player.count}</td>
                        </tr>
                    ))}
                    </tbody>
                </table>
            </div>
        </div>
    );
}
