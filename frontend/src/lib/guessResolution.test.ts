import {describe, expect, it} from 'vitest';
import {isActionStateForPick, resolveEffectiveResponse, resolveSubmissionTarget, type RoundSubmission} from './guessResolution';
import {loadDay, saveRound, type RoundResult} from './storage';

describe('isActionStateForPick', () => {
    it('accepts a state stamped for the same pick, failure or success', () => {
        expect(isActionStateForPick({ok: true, appId: 10, response: {appId: 10, totalReviews: 1, actualBucket: 'A', correct: true}}, 10)).toBe(true);
        expect(isActionStateForPick({ok: false, appId: 10, error: 'Upstream error 500'}, 10)).toBe(true);
    });

    it('rejects a failed state stamped for another pick', () => {
        // The round changed while the submit was in flight: the previous round's error
        // banner and sign-out notice must not be attributed to the new round.
        const stale = {ok: false, appId: 11, unauthorized: true, error: 'unauthorized'};
        expect(isActionStateForPick(stale, 10)).toBe(false);
    });

    it('rejects unstamped states (initial state, pre-stamp action results)', () => {
        expect(isActionStateForPick({ok: false}, 10)).toBe(false);
        expect(isActionStateForPick(null, 10)).toBe(false);
        expect(isActionStateForPick(undefined, 10)).toBe(false);
    });
});

describe('resolveEffectiveResponse', () => {
    const stored: RoundResult = {
        appId: 10,
        selectedLabel: 'A',
        actualBucket: 'A',
        totalReviews: 50,
        correct: true,
    };

    it('prefers a fresh, successful action state above all else', () => {
        const state = {ok: true, response: {appId: 10, totalReviews: 99, actualBucket: 'C', correct: false}};
        expect(resolveEffectiveResponse(state, stored, 10)).toBe(state.response);
    });

    it('rejects an action response graded for another pick', () => {
        // The round changed while the submit was in flight: the response carries the
        // previous round's appId, so it must not be shown as this round's result.
        const state = {ok: true, response: {appId: 11, totalReviews: 99, actualBucket: 'C', correct: false}};

        expect(resolveEffectiveResponse(state, undefined, 10)).toBeNull();
        expect(resolveEffectiveResponse(state, stored, 10)).toEqual({
            appId: 10,
            totalReviews: 50,
            actualBucket: 'A',
            correct: true,
        });
    });

    it('falls back to the stored round result when there is no live response', () => {
        expect(resolveEffectiveResponse({ok: false}, stored, 10)).toEqual({
            appId: 10,
            totalReviews: 50,
            actualBucket: 'A',
            correct: true,
        });
    });

    it('returns null when no source has data', () => {
        expect(resolveEffectiveResponse(null, undefined, 10)).toBeNull();
    });
});

describe('resolveSubmissionTarget', () => {
    const submission: RoundSubmission = {
        gameDate: '2024-01-01',
        roundIndex: 1,
        totalRounds: 5,
        appId: 10,
        pickName: 'Game',
        label: 'A',
    };
    const okState = {ok: true, response: {appId: 10, totalReviews: 5, actualBucket: 'A', correct: true}};

    it('targets the captured round even after the scope reset cleared the live label', () => {
        // The player moved to another round while this submit was in flight; the
        // captured identity still points at round 1, where the guess was made.
        expect(resolveSubmissionTarget(okState, submission)).toEqual({
            gameDate: '2024-01-01',
            roundIndex: 1,
            totalRounds: 5,
            result: {
                appId: 10,
                pickName: 'Game',
                selectedLabel: 'A',
                actualBucket: 'A',
                totalReviews: 5,
                correct: true,
            },
        });
    });

    it('refuses a response graded for another submission', () => {
        const other = {ok: true, response: {appId: 11, totalReviews: 5, actualBucket: 'A', correct: true}};
        expect(resolveSubmissionTarget(other, submission)).toBeNull();
    });

    it('refuses failed responses and submissions without a game date', () => {
        expect(resolveSubmissionTarget({ok: false, appId: 10, error: 'Upstream error 500'}, submission)).toBeNull();
        expect(resolveSubmissionTarget(okState, {...submission, gameDate: undefined})).toBeNull();
        expect(resolveSubmissionTarget(okState, null)).toBeNull();
    });

    it('lands the resolved response in the stored day under the captured round', () => {
        // Minimal localStorage stand-in: saveRound reads window.localStorage, which
        // does not exist in the node test env.
        const store = new Map<string, string>();
        (globalThis as Record<string, unknown>).window = {
            localStorage: {
                getItem: (k: string) => store.get(k) ?? null,
                setItem: (k: string, v: string) => {
                    store.set(k, v);
                },
            },
        };
        try {
            const target = resolveSubmissionTarget(okState, submission);
            if (!target) throw new Error('expected a save target');
            saveRound(target.gameDate, target.roundIndex, target.totalRounds, target.result);
            expect(loadDay('2024-01-01')?.results[1]).toEqual(target.result);
        } finally {
            delete (globalThis as Record<string, unknown>).window;
        }
    });
});
