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
    const flat = days.flatMap(d => d.rounds.map(r => ({...r, date: d.date})));
    return flat.sort((a, b) => {
        if (a.date === b.date) return (a.roundIndex || 0) - (b.roundIndex || 0);
        return a.date.localeCompare(b.date);
    });
}
