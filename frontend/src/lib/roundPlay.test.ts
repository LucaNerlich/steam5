import {describe, expect, it} from 'vitest';
import {deriveRoundPlay} from './roundPlay';
import type {RoundResult} from './storage';

const stored: RoundResult = {
    appId: 10,
    selectedLabel: 'A',
    actualBucket: 'A',
    totalReviews: 50,
    correct: true,
};

const base = {
    appId: 10,
    actionState: null,
    stored: undefined,
    selection: null,
    pending: false,
    signedIn: false,
} as const;

describe('deriveRoundPlay', () => {
    it('is open with no result and no pending submission', () => {
        expect(deriveRoundPlay(base)).toEqual({phase: 'open', selection: null, signedOut: false});
        expect(deriveRoundPlay({...base, selection: 'B'}))
            .toEqual({phase: 'open', selection: 'B', signedOut: false});
    });

    it('is submitting while a guess is in flight', () => {
        expect(deriveRoundPlay({...base, pending: true, selection: 'B'}))
            .toEqual({phase: 'submitting', selection: 'B', signedOut: false});
    });

    it('hides the previous attempt error while the retry is in flight', () => {
        const actionState = {ok: false, appId: 10, error: 'Upstream error 500'};
        expect(deriveRoundPlay({...base, actionState, pending: true, selection: 'B'}).phase)
            .toBe('submitting');
    });

    it('is rejected on a failed submission for this pick, keeping the selection retryable', () => {
        const actionState = {ok: false, appId: 10, error: 'Upstream error 500'};

        expect(deriveRoundPlay({...base, actionState, selection: 'B'})).toEqual({
            phase: 'rejected',
            selection: 'B',
            error: 'Upstream error 500',
            signedOut: false,
        });
    });

    it('ignores an error stamped for another pick', () => {
        // The round page is reused across rounds; the previous round's banner
        // must not appear over the newly opened round.
        const stale = {ok: false, appId: 11, error: 'Upstream error 500'};
        expect(deriveRoundPlay({...base, actionState: stale})).toEqual({
            phase: 'open',
            selection: null,
            signedOut: false,
        });
    });

    it('is graded by a live response for this pick', () => {
        const response = {appId: 10, totalReviews: 5, actualBucket: 'A', correct: true};
        const actionState = {ok: true, appId: 10, response};

        expect(deriveRoundPlay({...base, actionState, selection: 'A'})).toEqual({
            phase: 'graded',
            selection: 'A',
            response,
            signedOut: false,
        });
    });

    it('ignores a live response graded for another pick', () => {
        const actionState = {ok: true, appId: 11, response: {appId: 11, totalReviews: 5, actualBucket: 'A', correct: true}};
        expect(deriveRoundPlay({...base, actionState})).toEqual({
            phase: 'open',
            selection: null,
            signedOut: false,
        });
    });

    it('is graded from a restored result, naming the stored bucket after the live selection cleared', () => {
        expect(deriveRoundPlay({...base, stored, selection: null})).toEqual({
            phase: 'graded',
            selection: 'A',
            response: {appId: 10, totalReviews: 50, actualBucket: 'A', correct: true},
            signedOut: false,
        });
    });

    it('stays graded while another submission is pending', () => {
        const actionState = {ok: true, appId: 10, response: {appId: 10, totalReviews: 5, actualBucket: 'A', correct: true}};
        expect(deriveRoundPlay({...base, actionState, pending: true}).phase).toBe('graded');
    });

    it('flags a session lost mid-round on a 401', () => {
        const actionState = {ok: false, appId: 10, unauthorized: true, error: 'unauthorized'};
        const play = deriveRoundPlay({...base, actionState, signedIn: true});

        expect(play.phase).toBe('rejected');
        expect(play.signedOut).toBe(true);
    });

    it('flags a guess that was saved anonymously while the UI showed signed in', () => {
        const actionState = {ok: true, appId: 10, persisted: false, response: {appId: 10, totalReviews: 5, actualBucket: 'A', correct: true}};
        const play = deriveRoundPlay({...base, actionState, signedIn: true});

        expect(play.phase).toBe('graded');
        expect(play.signedOut).toBe(true);
    });

    it('does not flag a genuine anonymous player', () => {
        const actionState = {ok: true, appId: 10, persisted: false, response: {appId: 10, totalReviews: 5, actualBucket: 'A', correct: true}};
        expect(deriveRoundPlay({...base, actionState, signedIn: false}).signedOut).toBe(false);
    });
});
