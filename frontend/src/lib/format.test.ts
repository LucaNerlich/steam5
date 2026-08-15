import {describe, expect, it} from 'vitest';
import {formatDate, formatPrice, formatRelativeTime, ordinal, placementTier} from './format';

describe('ordinal', () => {
    it('uses st/nd/rd/th for the common cases', () => {
        expect(ordinal(1)).toBe('1st');
        expect(ordinal(2)).toBe('2nd');
        expect(ordinal(3)).toBe('3rd');
        expect(ordinal(4)).toBe('4th');
        expect(ordinal(21)).toBe('21st');
        expect(ordinal(22)).toBe('22nd');
        expect(ordinal(23)).toBe('23rd');
    });

    it('uses th for the 11/12/13 teens exception', () => {
        expect(ordinal(11)).toBe('11th');
        expect(ordinal(12)).toBe('12th');
        expect(ordinal(13)).toBe('13th');
        expect(ordinal(111)).toBe('111th');
        expect(ordinal(112)).toBe('112th');
        expect(ordinal(113)).toBe('113th');
    });
});

describe('placementTier', () => {
    it('maps the podium to medal tiers and the rest to neutral', () => {
        expect(placementTier(1)).toBe('gold');
        expect(placementTier(2)).toBe('silver');
        expect(placementTier(3)).toBe('bronze');
        expect(placementTier(4)).toBe('neutral');
        expect(placementTier(0)).toBe('neutral');
    });
});

describe('formatDate', () => {
    it('formats an ISO date string (TZ pinned to UTC in config)', () => {
        expect(formatDate('2024-01-15', 'en-US')).toBe('Jan 15, 2024');
    });

    it('accepts a Date instance', () => {
        expect(formatDate(new Date('2024-12-31T00:00:00Z'), 'en-US')).toBe('Dec 31, 2024');
    });

    it('returns an empty string for an unparseable date', () => {
        expect(formatDate('not-a-date', 'en-US')).toBe('');
        expect(formatDate('2024-02-31', 'en-US')).toBe('');
    });
});

describe('formatRelativeTime', () => {
    const now = new Date('2026-07-31T12:00:00Z');

    it('returns just now for recent timestamps', () => {
        expect(formatRelativeTime('2026-07-31T11:59:30Z', now)).toBe('just now');
    });

    it('returns minutes and hours for same-day ages', () => {
        expect(formatRelativeTime('2026-07-31T11:50:00Z', now)).toBe('10m');
        expect(formatRelativeTime('2026-07-31T09:00:00Z', now)).toBe('3h');
    });

    it('returns days under a week and a short date beyond that', () => {
        expect(formatRelativeTime('2026-07-29T12:00:00Z', now)).toBe('2d');
        expect(formatRelativeTime('2026-07-20T12:00:00Z', now, 'en-US')).toBe('Jul 20, 2026');
    });

    it('returns an empty string for invalid dates', () => {
        expect(formatRelativeTime('not-a-date', now)).toBe('');
    });
});

describe('formatPrice', () => {
    it('formats cents as a currency amount', () => {
        expect(formatPrice(1999, 'USD', 'en-US')).toBe('$19.99');
    });

    it('treats nullish cents as zero', () => {
        expect(formatPrice(undefined as unknown as number, 'USD', 'en-US')).toBe('$0.00');
    });

    it('falls back to a plain amount for an invalid currency code', () => {
        expect(formatPrice(500, 'NOT_A_CURRENCY', 'en-US')).toBe('5.00 NOT_A_CURRENCY');
    });
});
