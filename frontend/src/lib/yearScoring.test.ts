import {describe, expect, it} from 'vitest';
import {effectiveYearHintsUsed, yearPointsForHintsUsed} from './yearScoring';

describe('yearScoring', () => {
    it('scores fewer points when hints were used', () => {
        expect(yearPointsForHintsUsed(5, 0)).toBe(5);
        expect(yearPointsForHintsUsed(5, 1)).toBe(4);
        expect(yearPointsForHintsUsed(5, 2)).toBe(3);
        expect(yearPointsForHintsUsed(5, 3)).toBe(2);
    });

    it('uses revealed hint count when server hints lag', () => {
        expect(effectiveYearHintsUsed(0, 1)).toBe(1);
        expect(effectiveYearHintsUsed(1, 2)).toBe(2);
    });
});
