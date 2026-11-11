import type {GuessResponse} from "@/types/review-game";
import type {RoundResult} from "@/lib/storage";
import {computeSignedOutDuringPlay} from "./authGuard";
import {isActionStateForPick, resolveEffectiveResponse, type RoundActionState} from "./guessResolution";

/**
 * What a round is showing right now.
 *
 * `ReviewGuesserRound` used to derive this from a handful of independent booleans
 * (`isPending`, `isGating`, `submittedFlag`, `shouldShowGuessControls`, an error
 * string, a response, a sign-out flag). Those could disagree — e.g. "submitted"
 * while the result was still null — and every consumer had to know which ones
 * could be trusted. One union makes the phases, and the legal combinations,
 * explicit.
 *
 * - open:       no result yet; the player may pick a bucket
 * - submitting: a guess (or the pre-submit auth probe) is in flight
 * - graded:     the round has a result (live, server, or localStorage)
 * - rejected:   the last submission failed; the player may retry
 */
export type RoundPlay =
    | { phase: "open"; selection: string | null; signedOut: false }
    | { phase: "submitting"; selection: string | null; signedOut: boolean }
    | { phase: "graded"; selection: string | null; response: GuessResponse; signedOut: boolean }
    | { phase: "rejected"; selection: string | null; error: string; signedOut: boolean };

export type RoundPlayInput = {
    appId: number;
    /** Latest `submitGuessAction` return for this round page (may be stale). */
    actionState: RoundActionState | null | undefined;
    /** Restored result for this round (localStorage or server). */
    stored: RoundResult | undefined;
    /** Live bucket selection; null after a round change or before any pick. */
    selection: string | null;
    /** True while the submit transition or the pre-submit auth probe is in flight. */
    pending: boolean;
    signedIn: boolean | null;
};

/**
 * Collapses the round's inputs into one phase. Pure so the precedence rules —
 * a stale response never grades the wrong pick, a failed guess stays retryable —
 * are testable without mounting the component.
 */
export function deriveRoundPlay(input: RoundPlayInput): RoundPlay {
    const {appId, actionState, stored, selection, pending, signedIn} = input;
    const stateForPick = isActionStateForPick(actionState, appId) ? actionState : undefined;
    const signedOut = computeSignedOutDuringPlay(signedIn, stateForPick);
    const response = resolveEffectiveResponse(actionState, stored, appId);

    if (response) {
        // The restored label wins: after a round change the live selection is cleared,
        // but the stored result still names the bucket that was guessed.
        return {phase: "graded", selection: stored?.selectedLabel ?? selection, response, signedOut};
    }
    // A retry is in flight: show the pending state, not the error from the previous attempt.
    if (pending) return {phase: "submitting", selection, signedOut};
    if (stateForPick && !stateForPick.ok && stateForPick.error) {
        // `ok: false` keeps the buttons enabled so the player can retry without a reload.
        return {phase: "rejected", selection, error: stateForPick.error, signedOut};
    }
    return {phase: "open", selection, signedOut: false};
}
