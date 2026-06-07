"use client";

import "@/styles/components/leaderboard.css";
import Image from "next/image";
import useSWR from "swr";
import {useCallback, useMemo, useState} from "react";
import {
    UserAchievement,
    getAchievementLabel,
    getAchievementTitle,
} from "@/lib/achievements";
import AchievementsTable from "@/components/AchievementsTable";

type LeaderEntry = {
    steamId: string;
    personaName: string;
    totalPoints: number;
    rounds: number;
    hits: number;
    flops: number;
    tooHigh: number;
    tooLow: number;
    avgPoints: number;
    streak: number;
    avatar?: string | null;
    avatarBlurdata?: string | null;
    profileUrl?: string | null;
};

const fetcher = (url: string) => fetch(url, {
    headers: {accept: 'application/json'},
    cache: 'no-cache' // Revalidate with server but allow caching for performance
}).then(r => {
    if (!r.ok) throw new Error(`Failed to load ${url}: ${r.status}`);
    return r.json();
});

export default function LeaderboardTable(props: {
    mode: 'today' | 'weekly' | 'weekly-floating' | 'season' | 'all';
    refreshMs?: number;
    ariaLabel?: string;
    initialData?: LeaderEntry[] | null;
    initialAchievements?: { data: UserAchievement[], serverOffsetMinutes: number } | null;
}) {

    let aria;
    let endpoint;
    switch (props.mode) {
        case 'today':
            endpoint = '/api/leaderboard/today';
            aria = props.ariaLabel ?? 'Today Leaderboard';
            break;
        case 'season':
            endpoint = '/api/leaderboard/season';
            aria = props.ariaLabel ?? 'Season Leaderboard';
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

    const refreshInterval = props.refreshMs ?? (props.mode === 'today' ? 60000 : 120000);

    const {data, error, isLoading} = useSWR<LeaderEntry[]>(endpoint, fetcher, {
        refreshInterval,
        revalidateOnFocus: true,
        focusThrottleInterval: refreshInterval,
        fallbackData: props.initialData || undefined,
    });

    // Determine timeframe for achievements based on leaderboard mode
    const achievementTimeframe = props.mode === 'today' ? 'daily' :
                                  props.mode === 'season' ? 'season' :
                                  (props.mode === 'weekly' || props.mode === 'weekly-floating') ? 'weekly' :
                                  'all';

    const endpointAchievements = `/api/leaderboard/achievements?timeframe=${achievementTimeframe}`;

    const { data: achievementsData } = useSWR<{data: UserAchievement[], serverOffsetMinutes: number}>(endpointAchievements, async (url) => {
        const response = await fetch(url, {headers: {accept: 'application/json'}});
        if (!response.ok) throw new Error(`Failed to load ${url}: ${response.status}`);
        const data = await response.json();
        const serverOffsetMinutes = parseInt(response.headers.get('X-Server-Timezone-Offset') || '0', 10);
        return { data, serverOffsetMinutes };
    }, {
        refreshInterval,
        revalidateOnFocus: true,
        focusThrottleInterval: refreshInterval,
        dedupingInterval: refreshInterval,
        fallbackData: props.initialAchievements || undefined,
    });

    // Extract achievements array and server offset
    const achievementsList = achievementsData?.data || [];
    const serverOffsetMinutes = achievementsData?.serverOffsetMinutes ?? 0;

    const achievementBySteamId = useMemo(() => {
        const m = new Map<string, UserAchievement>();
        if (Array.isArray(achievementsList)) {
            for (const a of achievementsList) {
                if (a?.steamId && a?.userAchievement) {
                    m.set(a.steamId, a);
                }
            }
        }
        return m;
    }, [achievementsList, props.mode]);

    type SortKey = 'personaName' | 'totalPoints' | 'rounds' | 'streak' | 'hits' | 'flops' | 'tooHigh' | 'tooLow' | 'avgPoints';
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

    const avgTotalPoints = useMemo(() => {
        const arr = Array.isArray(data) ? data : [];
        if (arr.length === 0) return 0;
        const sum = arr.reduce((acc, e) => acc + (typeof e.totalPoints === 'number' ? e.totalPoints : 0), 0);
        return sum / arr.length;
    }, [data]);

    if (error) return <p className="text-muted">Failed to load leaderboard. Please try again.</p>;
    if (isLoading && !props.initialData) return <p className="text-muted">Loading leaderboard…</p>;
    if (!data) return <p className="text-muted">Loading leaderboard…</p>;

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
            <div className="leaderboard__scroll">
                <table className="leaderboard__table" aria-label={aria}>
                    <thead>
                    <tr>
                        <th scope="col" className="num">#</th>
                        <SortableTH label="Player" keyName={'personaName'}/>
                        <SortableTH label="Points" keyName={'totalPoints'} alignNum/>
                        <SortableTH label="Rounds" keyName={'rounds'} alignNum/>
                        <SortableTH label="Streak" keyName={'streak'} title={'Uninterrupted daily-challenges'}
                                    alignNum/>
                        <SortableTH label="Hits" keyName={'hits'} title={'Correct guess'} alignNum/>
                        <SortableTH label="Flops" keyName={'flops'} title={'Zero-point rounds (3+ buckets off)'} alignNum/>
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
                                    <a href={`/profile/${encodeURIComponent(entry.steamId)}`}
                                       className="leaderboard__profile-link">
                                        <strong>{entry.personaName || 'no-name'}</strong>
                                    </a>
                                    {(() => {
                                        const achievement = achievementBySteamId.get(entry.steamId);
                                        const k = achievement?.userAchievement;
                                        const lbl = getAchievementLabel(k);
                                        return lbl ? (
                                            <span
                                                className="leaderboard__achievement"
                                                title={getAchievementTitle(k, achievement, serverOffsetMinutes) ?? lbl ?? undefined}
                                                style={{
                                                    marginLeft: 8,
                                                    padding: '2px 6px',
                                                    borderRadius: 999,
                                                    backgroundColor: '#eef2ff',
                                                    color: '#1e3a8a',
                                                    fontSize: '0.75rem',
                                                    fontWeight: 600,
                                                }}
                                            >
                                                {lbl}
                                            </span>
                                        ) : null;
                                    })()}
                                </div>
                            </td>
                            <td className="num">{entry.totalPoints}</td>
                            <td className="num">{entry.rounds}</td>
                            <td className="num">{entry.streak}</td>
                            <td className="num">{entry.hits}</td>
                            <td className="num">{entry.flops}</td>
                            <td className="num">{entry.tooHigh}</td>
                            <td className="num">{entry.tooLow}</td>
                            <td className="num">{entry.avgPoints.toFixed(2)}</td>
                        </tr>
                    ))}
                    </tbody>
                </table>
            </div>
            <div className="leaderboard__subline" aria-live="polite">
                Average points:&nbsp;<strong>{avgTotalPoints.toFixed(2)}</strong>
            </div>
            
            <AchievementsTable
                achievements={achievementsList}
                serverOffsetMinutes={serverOffsetMinutes}
                leaderboardEntries={data ?? []}
            />
        </div>
    );
}


