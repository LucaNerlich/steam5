import {describe, expect, it} from 'vitest';
import {formatAwardMetric, groupAwardsByCategory, rankClassName} from './seasons';
import type {AwardView} from '@/types/seasons';

const award = (over: Partial<AwardView>): AwardView => ({
    category: 'MOST_POINTS',
    categoryLabel: 'Most Points',
    placementLevel: 1,
    steamId: 'x',
    personaName: 'Player',
    metricValue: 0,
    ...over,
});

describe('groupAwardsByCategory', () => {
    it('returns an empty array for empty or non-array input', () => {
        expect(groupAwardsByCategory([])).toEqual([]);
        expect(groupAwardsByCategory(null as unknown as AwardView[])).toEqual([]);
    });

    it('groups awards by category, preserving label and order', () => {
        const groups = groupAwardsByCategory([
            award({category: 'MOST_POINTS', categoryLabel: 'Most Points', metricValue: 1}),
            award({category: 'MOST_HITS', categoryLabel: 'Most Hits', metricValue: 2}),
            award({category: 'MOST_POINTS', categoryLabel: 'Most Points', metricValue: 3}),
        ]);
        expect(groups).toHaveLength(2);
        expect(groups[0]).toMatchObject({category: 'MOST_POINTS', label: 'Most Points'});
        expect(groups[0].awards.map(a => a.metricValue)).toEqual([1, 3]);
        expect(groups[1]).toMatchObject({category: 'MOST_HITS', label: 'Most Hits'});
    });
});

describe('formatAwardMetric', () => {
    it('formats each known category with its unit', () => {
        expect(formatAwardMetric({category: 'MOST_POINTS', metricValue: 5})).toBe('5 pts');
        expect(formatAwardMetric({category: 'MOST_HITS', metricValue: 5})).toBe('5 hits');
        expect(formatAwardMetric({category: 'HIGHEST_AVG_POINTS_PER_DAY', metricValue: 250})).toBe('2.50 avg pts/day');
    });

    it('pluralises the streak unit', () => {
        expect(formatAwardMetric({category: 'LONGEST_STREAK', metricValue: 1})).toBe('1 day');
        expect(formatAwardMetric({category: 'LONGEST_STREAK', metricValue: 3})).toBe('3 days');
    });

    it('formats an unknown category as a bare number', () => {
        expect(formatAwardMetric({category: 'SOMETHING_ELSE', metricValue: 7})).toBe('7');
    });
});

describe('rankClassName', () => {
    it('appends a medal modifier for the podium and none for neutral', () => {
        expect(rankClassName(1)).toBe('season-card__rank season-card__rank--gold');
        expect(rankClassName(3)).toBe('season-card__rank season-card__rank--bronze');
        expect(rankClassName(4)).toBe('season-card__rank');
    });
});
