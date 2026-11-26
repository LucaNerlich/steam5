import {afterEach, describe, expect, it, vi} from 'vitest';
import {cutoffDateString} from './rounds';

afterEach(() => {
    vi.useRealTimers();
});

describe('cutoffDateString', () => {
    it('anchors the window on the given UTC day, not the clock', () => {
        // Three cards derived this from new Date() during render while their siblings used
        // the server's day; a midnight rollover made them disagree and broke hydration.
        vi.useFakeTimers();
        vi.setSystemTime(new Date('2099-01-03T00:30:00Z'));

        expect(cutoffDateString(30, '2099-01-02')).toBe('2098-12-03');
        expect(cutoffDateString(30, '2099-01-02')).not.toBe(cutoffDateString(30, '2099-01-03'));
    });

    it('rolls over months and years', () => {
        expect(cutoffDateString(1, '2099-01-01')).toBe('2098-12-31');
        expect(cutoffDateString(1, '2099-03-01')).toBe('2099-02-28');
    });
});
