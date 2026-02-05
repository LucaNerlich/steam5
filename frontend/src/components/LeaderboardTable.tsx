"use client";

import "@/styles/components/leaderboard.css";
import Image from "next/image";
import useSWR from "swr";
import {useCallback, useMemo, useState} from "react";

export type LeaderEntry = {
    steamId: string;
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
    ariaLabel?: string
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

    const {data, error, isLoading} = useSWR<LeaderEntry[]>(endpoint, fetcher, {
        refreshInterval: props.refreshMs ?? (props.mode === 'today' ? 5000 : 10000),
        revalidateOnFocus: true,
    });

    // Determine timeframe for achievements based on leaderboard mode
    const achievementTimeframe = props.mode === 'today' ? 'daily' :
                                  props.mode === 'season' ? 'season' :
                                  (props.mode === 'weekly' || props.mode === 'weekly-floating') ? 'weekly' :
                                  'all';
    
    const endpointAchievements = `/api/leaderboard/achievements?timeframe=${achievementTimeframe}`;

    type UserAchievement = {
        steamId: string;
        userAchievement: string;
        avgMinutes?: number;      // Early Bird / Night Owl
        avgPoints?: number;       // Sharpshooter
        perfectRounds?: number;  // Bullseye
        perfectDays?: number;     // Perfect Day
        totalSeconds?: number;    // Cheetah / Sloth
    };

    const ACHIEVEMENT_LABELS: Record<string, string> = {
        EARLY_BIRD: 'Early Bird',
        NIGHT_OWL: 'Night Owl',
        SHARPSHOOTER: 'Sharpshooter',
        BULLSEYE: 'Bullseye',
        PERFECT_DAY: 'Perfect Day',
        CHEETAH: 'Cheetah',
        SLOTH: 'Sloth',
    };

    const ACHIEVEMENT_TITLES: Record<string, string> = {
        EARLY_BIRD: 'Plays the earliest ☀',
        NIGHT_OWL: 'Plays the latest 🌙',
        SHARPSHOOTER: 'Highest average points per guess 🔫',
        BULLSEYE: 'Most perfect rounds (points = 5) 🎯',
        PERFECT_DAY: 'Most days with perfect total points 🎩',
        CHEETAH: 'Least time between first and last guess per day 🐆',
        SLOTH: 'Most time between first and last guess per day 🦥',
    };

    const { data: achievementsData } = useSWR<{data: UserAchievement[], serverOffsetMinutes: number}>(endpointAchievements, async (url) => {
        const response = await fetch(url, {headers: {accept: 'application/json'}});
        if (!response.ok) throw new Error(`Failed to load ${url}: ${response.status}`);
        const data = await response.json();
        const serverOffsetMinutes = parseInt(response.headers.get('X-Server-Timezone-Offset') || '0', 10);
        return { data, serverOffsetMinutes };
    }, {
        refreshInterval: props.refreshMs ?? (props.mode === 'today' ? 5000 : 10000),
        revalidateOnFocus: true,
        dedupingInterval: 2000, // Allow refetch after 2 seconds
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

    const getAchievementLabel = useCallback((key: string | null | undefined) => {
        if (!key) return null;
        return ACHIEVEMENT_LABELS[key] ?? key.split('_').map(s => s.charAt(0) + s.slice(1).toLowerCase()).join(' ');
    }, []);

    const formatDuration = useCallback((seconds: number): string => {
        if (seconds < 60) return `${seconds}s`;
        const minutes = Math.floor(seconds / 60);
        const secs = seconds % 60;
        if (minutes < 60) return `${minutes}m ${secs}s`;
        const hours = Math.floor(minutes / 60);
        const mins = minutes % 60;
        return `${hours}h ${mins}m`;
    }, []);

    const formatTimeOfDay = useCallback((minutesSinceMidnightServer: number, serverOffsetMinutes: number = 0): string => {
        // Convert server time (minutes since midnight in server timezone) to local time
        // 1. Server minutes since midnight -> UTC minutes since midnight
        const utcMinutesSinceMidnight = minutesSinceMidnightServer - serverOffsetMinutes;
        // 2. Get current date in UTC to establish a reference point
        const now = new Date();
        const utcDate = new Date(Date.UTC(
            now.getUTCFullYear(),
            now.getUTCMonth(),
            now.getUTCDate(),
            0, 0, 0, 0
        ));
        // 3. Add UTC minutes to get UTC time
        const utcTime = new Date(utcDate.getTime() + utcMinutesSinceMidnight * 60000);
        // 4. Convert to local time
        const localHours = utcTime.getHours();
        const localMins = utcTime.getMinutes();
        const period = localHours >= 12 ? 'PM' : 'AM';
        const displayHours = localHours === 0 ? 12 : localHours > 12 ? localHours - 12 : localHours;
        return `${displayHours}:${localMins.toString().padStart(2, '0')} ${period}`;
    }, []);

    const getAchievementTitle = useCallback((key: string | null | undefined, achievement?: UserAchievement) => {
        if (!key) return undefined;
        
        const baseTitle = ACHIEVEMENT_TITLES[key];
        if (!achievement) return baseTitle;
        
        // Add metric information to tooltip
        switch (key) {
            case 'EARLY_BIRD':
            case 'NIGHT_OWL':
                return (achievement.avgMinutes !== undefined && achievement.avgMinutes !== null)
                    ? `${baseTitle} (avg: ${formatTimeOfDay(achievement.avgMinutes, serverOffsetMinutes)})`
                    : baseTitle;
            case 'SHARPSHOOTER':
                return (achievement.avgPoints !== undefined && achievement.avgPoints !== null)
                    ? `${baseTitle} (${achievement.avgPoints.toFixed(2)} pts/guess)`
                    : baseTitle;
            case 'BULLSEYE':
                return (achievement.perfectRounds !== undefined && achievement.perfectRounds !== null)
                    ? `${baseTitle} (${achievement.perfectRounds} perfect rounds)`
                    : baseTitle;
            case 'PERFECT_DAY':
                return (achievement.perfectDays !== undefined && achievement.perfectDays !== null)
                    ? `${baseTitle} (${achievement.perfectDays} perfect days)`
                    : baseTitle;
            case 'CHEETAH':
            case 'SLOTH':
                return (achievement.totalSeconds !== undefined && achievement.totalSeconds !== null)
                    ? `${baseTitle} (total: ${formatDuration(achievement.totalSeconds)})`
                    : baseTitle;
            default:
                return baseTitle;
        }
    }, [formatDuration, formatTimeOfDay]);

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

    const avgTotalPoints = useMemo(() => {
        const arr = Array.isArray(data) ? data : [];
        if (arr.length === 0) return 0;
        const sum = arr.reduce((acc, e) => acc + (typeof e.totalPoints === 'number' ? e.totalPoints : 0), 0);
        return sum / arr.length;
    }, [data]);

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
                                                title={getAchievementTitle(k, achievement) ?? lbl ?? undefined}
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
            
            {achievementsList && achievementsList.length > 0 && (
                <div className="leaderboard__achievements">
                    <h3 className="leaderboard__achievements-title">Achievements</h3>
                    <table className="leaderboard__achievements-table">
                        <thead>
                            <tr>
                                <th>Achievement</th>
                                <th>Winner</th>
                                <th className="num">Metric</th>
                            </tr>
                        </thead>
                        <tbody>
                            {Object.entries(ACHIEVEMENT_LABELS).map(([key, label]) => {
                                const achievement = achievementsList.find(a => a.userAchievement === key);
                                if (!achievement) return null;
                                
                                // Only show achievements for users who are in the current leaderboard
                                const entry = data?.find(e => e.steamId === achievement.steamId);
                                if (!entry) return null;
                                const metricText = (() => {
                                    switch (key) {
                                        case 'EARLY_BIRD':
                                        case 'NIGHT_OWL':
                                            if (achievement.avgMinutes !== undefined && achievement.avgMinutes !== null) {
                                                try {
                                                    return formatTimeOfDay(achievement.avgMinutes, serverOffsetMinutes);
                                                } catch (e) {
                                                    console.error('Error formatting time:', e, achievement);
                                                    return `${Math.round(achievement.avgMinutes)} min`;
                                                }
                                            }
                                            return null;
                                        case 'SHARPSHOOTER':
                                            return (achievement.avgPoints !== undefined && achievement.avgPoints !== null)
                                                ? `${achievement.avgPoints.toFixed(2)} pts/guess`
                                                : null;
                                        case 'BULLSEYE':
                                            return (achievement.perfectRounds !== undefined && achievement.perfectRounds !== null)
                                                ? `${achievement.perfectRounds} rounds`
                                                : null;
                                        case 'PERFECT_DAY':
                                            return (achievement.perfectDays !== undefined && achievement.perfectDays !== null)
                                                ? `${achievement.perfectDays} days`
                                                : null;
                                        case 'CHEETAH':
                                        case 'SLOTH':
                                            return (achievement.totalSeconds !== undefined && achievement.totalSeconds !== null)
                                                ? formatDuration(achievement.totalSeconds)
                                                : null;
                                        default:
                                            return null;
                                    }
                                })();
                                
                                const icon = (() => {
                                    switch (key) {
                                        case 'EARLY_BIRD': return '☀';
                                        case 'NIGHT_OWL': return '🌙';
                                        case 'SHARPSHOOTER': return '🔫';
                                        case 'BULLSEYE': return '🎯';
                                        case 'PERFECT_DAY': return '🎩';
                                        case 'CHEETAH': return '🐆';
                                        case 'SLOTH': return '🦥';
                                        default: return '';
                                    }
                                })();
                                
                                return (
                                    <tr key={key}>
                                        <td data-label="Achievement">
                                            <span className="leaderboard__achievement-label">
                                                <span className="leaderboard__achievement-icon">{icon}</span>
                                                {label}
                                            </span>
                                            <span className="leaderboard__achievement-metric-mobile num">
                                                {metricText || '—'}
                                            </span>
                                        </td>
                                        <td data-label="Winner">
                                            <div className="leaderboard__achievement-winner">
                                                {entry?.avatar && (
                                                    <div className="leaderboard__achievement-avatar-wrap"
                                                         style={{backgroundImage: entry.avatarBlurdata ? `url(${entry.avatarBlurdata})` : undefined}}>
                                                        <Image className="leaderboard__achievement-avatar"
                                                               src={entry.avatar}
                                                               placeholder={'empty'}
                                                               alt=""
                                                               width={20}
                                                               height={20}/>
                                                    </div>
                                                )}
                                                <a href={`/profile/${encodeURIComponent(achievement.steamId)}`}
                                                   className="leaderboard__achievement-name">
                                                    {entry?.personaName || achievement.steamId}
                                                </a>
                                            </div>
                                        </td>
                                        <td className="num leaderboard__achievement-metric-desktop" data-label="Metric">
                                            {metricText || '—'}
                                        </td>
                                    </tr>
                                );
                            })}
                        </tbody>
                    </table>
                </div>
            )}
        </div>
    );
}


