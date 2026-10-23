export type OgLeader = { totalPoints: number; avgPoints: number };
export type OgBadge = { label: string; color: string };

export function percentileRank(value: number, values: number[]): number {
    if (values.length === 0) return 100;
    let below = 0;
    for (const v of values) if (v <= value) below++;
    const pct = 100 - Math.floor((below / values.length) * 100);
    return Math.max(1, Math.min(99, pct));
}

export function rankBadge(pctTop: number): OgBadge {
    if (pctTop <= 1) return {label: "S+", color: "#22c55e"};
    if (pctTop <= 5) return {label: "S", color: "#10b981"};
    if (pctTop <= 10) return {label: "A", color: "#34d399"};
    if (pctTop <= 25) return {label: "B", color: "#fde047"};
    if (pctTop <= 50) return {label: "C", color: "#f59e0b"};
    return {label: "Rookie", color: "#ef4444"};
}

/**
 * Percentiles and badge for a profile card. A missing or empty leaderboard means
 * "unknown": no percentile and no badge, never the worst tier.
 */
export function profileRank(
    leaders: OgLeader[] | null | undefined,
    totalPoints: number,
    avgPoints: number,
): {totalPct: number | null; avgPct: number | null; badge: OgBadge | null} {
    if (!leaders || leaders.length === 0) {
        return {totalPct: null, avgPct: null, badge: null};
    }
    const totalPct = percentileRank(totalPoints, leaders.map(l => l.totalPoints));
    const avgPct = percentileRank(avgPoints, leaders.map(l => l.avgPoints));
    return {totalPct, avgPct, badge: rankBadge(totalPct)};
}
