import {describe, expect, it} from 'vitest';
import {
    achievementMetricText,
    formatDuration,
    formatTimeOfDay,
    getAchievementLabel,
    getAchievementTitle,
} from './achievements';

describe('getAchievementLabel', () => {
    it('returns null for a missing key', () => {
        expect(getAchievementLabel(null)).toBeNull();
        expect(getAchievementLabel(undefined)).toBeNull();
        expect(getAchievementLabel('')).toBeNull();
    });

    it('returns the known label for a known key', () => {
        expect(getAchievementLabel('EARLY_BIRD')).toBe('Early Bird');
        expect(getAchievementLabel('SHARPSHOOTER')).toBe('Sharpshooter');
    });

    it('title-cases an unknown SCREAMING_SNAKE key as a fallback', () => {
        expect(getAchievementLabel('SOME_NEW_BADGE')).toBe('Some New Badge');
    });
});

describe('formatDuration', () => {
    it('formats sub-minute durations in seconds', () => {
        expect(formatDuration(0)).toBe('0s');
        expect(formatDuration(59)).toBe('59s');
    });

    it('formats sub-hour durations as minutes and seconds', () => {
        expect(formatDuration(60)).toBe('1m 0s');
        expect(formatDuration(125)).toBe('2m 5s');
    });

    it('formats hour-plus durations as hours and minutes', () => {
        expect(formatDuration(3600)).toBe('1h 0m');
        expect(formatDuration(3661)).toBe('1h 1m');
    });
});

describe('formatTimeOfDay (TZ pinned to UTC)', () => {
    it('formats minutes-since-midnight as a 12-hour clock', () => {
        expect(formatTimeOfDay(0)).toBe('12:00 AM');
        expect(formatTimeOfDay(13 * 60)).toBe('1:00 PM');
        expect(formatTimeOfDay(12 * 60)).toBe('12:00 PM');
        expect(formatTimeOfDay(9 * 60 + 5)).toBe('9:05 AM');
    });

    it('renders the UTC value the backend reports, with no offset applied', () => {
        // GuessRepository averages the UTC hour/minute, so a value of 60 is 1 AM UTC. The
        // function used to subtract the server's own offset from it a second time, which showed
        // 12:00 AM on a +60min instance.
        expect(formatTimeOfDay(60)).toBe('1:00 AM');
        expect(formatTimeOfDay(22 * 60 + 30)).toBe('10:30 PM');
    });

    it('wraps past midnight rather than printing a negative time', () => {
        expect(formatTimeOfDay(23 * 60 + 40)).toBe('11:40 PM');
        expect(formatTimeOfDay(5 * 60)).toBe('5:00 AM');
    });

    it('carries fractional minutes that round to 60 into the next hour', () => {
        // 3:59.6 rounds to 4:00 instead of rendering "3:60".
        expect(formatTimeOfDay(3 * 60 + 59.6)).toBe('4:00 AM');
    });
});

describe('getAchievementTitle', () => {
    it('returns undefined for a missing key', () => {
        expect(getAchievementTitle(null)).toBeUndefined();
    });

    it('returns the base title when no achievement detail is supplied', () => {
        expect(getAchievementTitle('BULLSEYE')).toBe('Most perfect rounds (points = 5) 🎯');
    });

    it('appends the metric detail when available', () => {
        expect(getAchievementTitle('BULLSEYE', {steamId: 'x', userAchievement: 'BULLSEYE', perfectRounds: 7}))
            .toBe('Most perfect rounds (points = 5) 🎯 (7 perfect rounds)');
        expect(getAchievementTitle('SHARPSHOOTER', {steamId: 'x', userAchievement: 'SHARPSHOOTER', avgPoints: 4.2}))
            .toBe('Highest average points per guess 🔫 (4.20 pts/guess)');
    });

    it('falls back to the base title when the relevant metric is absent', () => {
        expect(getAchievementTitle('BULLSEYE', {steamId: 'x', userAchievement: 'BULLSEYE'}))
            .toBe('Most perfect rounds (points = 5) 🎯');
    });
});

describe('achievementMetricText', () => {
    const base = {steamId: 'x', userAchievement: 'k'};

    it('formats per-category metrics', () => {
        expect(achievementMetricText('SHARPSHOOTER', {...base, avgPoints: 3.456})).toBe('3.46 pts/guess');
        expect(achievementMetricText('BULLSEYE', {...base, perfectRounds: 4})).toBe('4 rounds');
        expect(achievementMetricText('PERFECT_DAY', {...base, perfectDays: 2})).toBe('2 days');
        expect(achievementMetricText('CHEETAH', {...base, totalSeconds: 125})).toBe('2m 5s');
        // 13:00 UTC as the backend reports it; the server's own offset is not applied again.
        expect(achievementMetricText('EARLY_BIRD', {...base, avgMinutes: 13 * 60})).toBe('1:00 PM');
    });

    it('returns null when the metric is absent or the category is unknown', () => {
        expect(achievementMetricText('SHARPSHOOTER', base)).toBeNull();
        expect(achievementMetricText('UNKNOWN', {...base, avgPoints: 1})).toBeNull();
    });
});
