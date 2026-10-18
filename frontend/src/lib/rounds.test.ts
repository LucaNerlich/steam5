import {describe, expect, it} from 'vitest';
import {bucketOutcome, flattenDayRounds, outcomeCounts} from './rounds';

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

describe('bucketOutcome', () => {
    it('treats equal labels as a hit', () => {
        expect(bucketOutcome('112-855', '112-855')).toBe('hit');
    });

    it('compares by bucket order, not by label text', () => {
        // Lexicographically "14-111" sorts AFTER "112-855", which inverted the verdict.
        expect(bucketOutcome('14-111', '112-855')).toBe('low');
        expect(bucketOutcome('112-855', '14-111')).toBe('high');
        expect(bucketOutcome('856-6467', '14-111')).toBe('high');
        expect(bucketOutcome('14-111', '856-6467')).toBe('low');
    });

    it('reports unknown when a label has no bucket order', () => {
        expect(bucketOutcome('', '112-855')).toBe('unknown');
        expect(bucketOutcome('112-855', 'n/a')).toBe('unknown');
    });
});

describe('outcomeCounts', () => {
    it('counts hits, too-high and too-low in bucket order', () => {
        expect(outcomeCounts([
            {selectedBucket: '112-855', actualBucket: '112-855'},
            {selectedBucket: '112-855', actualBucket: '14-111'},
            {selectedBucket: '14-111', actualBucket: '112-855'},
            {selectedBucket: '1-13', actualBucket: '14-111'},
            {selectedBucket: '856-6467', actualBucket: '1-13'},
        ])).toEqual({hits: 1, tooHigh: 2, tooLow: 2});
    });

    it('ignores rounds with an unparseable label', () => {
        expect(outcomeCounts([{selectedBucket: '', actualBucket: ''}]))
            .toEqual({hits: 0, tooHigh: 0, tooLow: 0});
    });
});
