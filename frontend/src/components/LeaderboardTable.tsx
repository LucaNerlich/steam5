"use client";

import "@/styles/components/leaderboard.css";
import Image from "next/image";
import useSWR from "swr";
import {useCallback, useMemo, useState} from "react";

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

    type SortKey = 'personaName' | 'totalPoints' | 'rounds' | 'streak' | 'hits' | 'tooHigh' | 'tooLow' | 'avgPoints';
    type SortDir = 'asc' | 'desc';

    const [sortKey, setSortKey] = useState<SortKey | null>(null);
    const [sortDir, setSortDir] = useState<SortDir>('desc');

    const defaultDirFor = useCallback((key: SortKey): SortDir => {
        return key === 'personaName' ? 'asc' : 'desc';
    }, []);

    const requestSort = useCallback((key: SortKey) => {
        if (sortKey === key) {
            setSortDir((d) => (d === 'asc' ? 'desc' : 'asc'));
        } else {
            setSortKey(key);
            setSortDir(defaultDirFor(key));
        }
    }, [sortKey, defaultDirFor]);

    const sorted = useMemo(() => {
        if (!Array.isArray(data)) return [] as LeaderEntry[];
        if (!sortKey) return data;
        const sortedCopy = [...data];
        sortedCopy.sort((a, b) => {
            const dir = sortDir === 'asc' ? 1 : -1;
            if (sortKey === 'personaName') {
                const an = (a.personaName || '').toLowerCase();
                const bn = (b.personaName || '').toLowerCase();
                const cmp = an.localeCompare(bn);
                return cmp * dir;
            }
            const av = (a[sortKey] ?? 0) as number;
            const bv = (b[sortKey] ?? 0) as number;
            if (av === bv) {
                // Tiebreak by name for deterministic ordering
                const an = (a.personaName || '').toLowerCase();
                const bn = (b.personaName || '').toLowerCase();
                return an.localeCompare(bn);
            }
            return av < bv ? -1 * dir : 1 * dir;
        });
        return sortedCopy;
    }, [data, sortDir, sortKey]);

    if (error) return <p className="text-muted">Failed to load leaderboard. Please try again.</p>;
    if (isLoading || !data) return <p className="text-muted">Loading leaderboard…</p>;

    const SortableTH = ({
                            label,
                            keyName,
                            title,
                            alignNum
                        }: { label: string; keyName: SortKey; title?: string; alignNum?: boolean }) => {
        const isActive = sortKey === keyName;
        const aria = isActive ? (sortDir === 'asc' ? 'ascending' : 'descending') : 'none';
        return (
            <th scope="col" className={alignNum ? 'num' : undefined} title={title} aria-sort={aria}>
                <button className={`sortable${isActive ? ' is-active' : ''}`} onClick={() => requestSort(keyName)}
                        aria-label={`Sort by ${label}`}>
                    {label}{isActive &&
                  <span className="sort-indicator" aria-hidden="true">{sortDir === 'asc' ? '▲' : '▼'}</span>}
                </button>
            </th>
        );
    };

    return (
        <div className="leaderboard">
            <table className="leaderboard__table" aria-label={aria}>
                <thead>
                <tr>
                    <th scope="col" className="num">#</th>
                    <SortableTH label="Player" keyName={'personaName'}/>
                    <SortableTH label="Points" keyName={'totalPoints'} alignNum/>
                    <SortableTH label="Rounds" keyName={'rounds'} alignNum/>
                    <SortableTH label="Streak" keyName={'streak'} title={'Uninterrupted daily-challenges'} alignNum/>
                    <SortableTH label="Hits" keyName={'hits'} title={'Correct guess'} alignNum/>
                    <SortableTH label="Too High" keyName={'tooHigh'} alignNum/>
                    <SortableTH label="Too Low" keyName={'tooLow'} alignNum/>
                    <SortableTH label="Avg" keyName={'avgPoints'} alignNum/>
                </tr>
                </thead>
                <tbody>
                {sorted.map((entry, i) => (
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


