/** A single round enriched with its game date, suitable for charting. */
export type FlatRound = {
    roundIndex: number;
    appId: number;
    appName?: string;
    selectedBucket: string;
    actualBucket: string;
    points: number;
    date: string;
};

type Day = { date: string; rounds: Omit<FlatRound, 'date'>[] };

/**
 * Flattens a day-grouped history into a chronological list of rounds.
 * Oldest date first; within a day, ascending roundIndex.
 */
export function flattenDayRounds(days: Day[]): FlatRound[] {
    const flat: FlatRound[] = [];
    for (const d of days) {
        for (const r of d.rounds) {
            if (r.selectedBucket && r.actualBucket && r.points != null) {
                flat.push({...r, date: d.date});
            }
        }
    }
    return flat.sort((a, b) => {
        if (a.date === b.date) return (a.roundIndex || 0) - (b.roundIndex || 0);
        return a.date.localeCompare(b.date);
    });
}

/**
 * yyyy-mm-dd cutoff for a window that ends today, `daysWindow` days back.
 *
 * The charts filter stored round dates (UTC game dates) with this string, so it is defined once
 * here rather than copied into each card. Note it derives the cutoff with local-date arithmetic
 * before slicing the ISO (UTC) representation — kept as-is from the copies so this refactor does
 * not change any chart's output.
 */
export function cutoffDateString(daysWindow: number): string {
    const now = new Date();
    const cutoffDate = new Date(now);
    cutoffDate.setDate(cutoffDate.getDate() - daysWindow);
    return cutoffDate.toISOString().slice(0, 10);
}

/** Leading number of a bucket label ("856-6467" -> 856); -1 when the label has none. */
export function leadingBucketOrder(label: string): number {
    const m = label?.match(/^(\d+)/);
    return m ? parseInt(m[1], 10) : -1;
}
