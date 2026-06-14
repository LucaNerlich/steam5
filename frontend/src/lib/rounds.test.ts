import {describe, expect, it} from 'vitest';
import {flattenDayRounds} from './rounds';

const round = (roundIndex: number, appId: number) => ({
    roundIndex,
    appId,
    selectedBucket: 'a',
    actualBucket: 'a',
    points: 5,
});

describe('flattenDayRounds', () => {
    it('returns an empty array for no days', () => {
        expect(flattenDayRounds([])).toEqual([]);
    });

    it('attaches the day date to each round', () => {
        const flat = flattenDayRounds([{date: '2024-01-01', rounds: [round(1, 10)]}]);
        expect(flat).toEqual([{...round(1, 10), date: '2024-01-01'}]);
    });

    it('orders by date ascending, then by roundIndex within a day', () => {
        const flat = flattenDayRounds([
            {date: '2024-01-02', rounds: [round(2, 22), round(1, 21)]},
            {date: '2024-01-01', rounds: [round(3, 13), round(1, 11)]},
        ]);
        expect(flat.map(r => [r.date, r.roundIndex])).toEqual([
            ['2024-01-01', 1],
            ['2024-01-01', 3],
            ['2024-01-02', 1],
            ['2024-01-02', 2],
        ]);
    });

    it('treats a missing roundIndex as 0 for ordering', () => {
        const flat = flattenDayRounds([
            {date: '2024-01-01', rounds: [round(1, 11), {...round(0, 10), roundIndex: undefined as unknown as number}]},
        ]);
        expect(flat.map(r => r.appId)).toEqual([10, 11]);
    });
});
