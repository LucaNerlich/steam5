export type RoundResultTier = 'exact' | 'close' | 'near' | 'far';

export function tierForDistance(distance: number): RoundResultTier {
    if (distance <= 0) return 'exact';
    if (distance === 1) return 'close';
    if (distance === 2) return 'near';
    return 'far';
}

export function tierEmoji(tier: RoundResultTier): string {
    if (tier === 'exact') return '🟩';
    if (tier === 'close') return '🟨';
    if (tier === 'near') return '🟧';
    return '🟥';
}

export function scoreForRound(buckets: string[], selectedLabel: string, actual: string): {
    bar: string; points: number; distance: number
} {
    const selectedIndex = buckets.indexOf(selectedLabel);
    const actualIndex = buckets.indexOf(actual);
    if (selectedIndex < 0 || actualIndex < 0) return {bar: '⬜', points: 0, distance: 0};
    const d = Math.abs(selectedIndex - actualIndex);
    const points = Math.max(0, 5 - 2 * d);
    const bar = tierEmoji(tierForDistance(d));
    return {bar, points, distance: d};
}


