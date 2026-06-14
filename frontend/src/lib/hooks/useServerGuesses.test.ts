import {describe, expect, it} from 'vitest';
import {toRoundResult} from './useServerGuesses';

describe('toRoundResult', () => {
    it('maps backend field names onto the RoundResult shape', () => {
        expect(toRoundResult({
            roundIndex: 2,
            appId: 42,
            selectedBucket: 'Positive',
            actualBucket: 'Positive',
            totalReviews: 1234,
        })).toEqual({
            appId: 42,
            pickName: undefined,
            selectedLabel: 'Positive',
            actualBucket: 'Positive',
            totalReviews: 1234,
            correct: true,
        });
    });

    it('marks incorrect when the buckets differ', () => {
        expect(toRoundResult({
            roundIndex: 1,
            appId: 7,
            selectedBucket: 'Mixed',
            actualBucket: 'Positive',
            totalReviews: 10,
        }).correct).toBe(false);
    });

    it('defaults missing actualBucket/totalReviews and treats unknown actual as incorrect', () => {
        expect(toRoundResult({roundIndex: 1, appId: 7, selectedBucket: 'Mixed'})).toEqual({
            appId: 7,
            pickName: undefined,
            selectedLabel: 'Mixed',
            actualBucket: '',
            totalReviews: 0,
            correct: false,
        });
    });
});
