import type {GuessResponse} from "@/types/review-game";
import type {RoundResult} from "@/lib/storage";

/**
 * The shape `submitGuessAction` returns. Declared structurally (not imported
 * from the server-action module) so the pure rules stay usable in tests.
 */
export type RoundActionState = {
    ok: boolean;
    appId?: number;
    error?: string;
    response?: GuessResponse;
    unauthorized?: boolean;
    persisted?: boolean;
};

/**
 * Whether an action state was produced for the given pick. `submitGuessAction`
 * stamps every branch it returns — failures included — with the requested appId,
 * so a previous round's error banner or sign-out notice cannot leak into the
 * round the player has just moved to.
 */
export function isActionStateForPick(
    state: RoundActionState | null | undefined,
    appId: number,
): boolean {
    return state?.appId === appId;
}

/**
 * The identity captured when a guess was submitted. It travels with the label so a
 * response that resolves after the player moved on is still persisted for the round
 * actually guessed — the round-scope reset clears the live selection, never this.
 */
export type RoundSubmission = {
    gameDate?: string;
    roundIndex: number;
    totalRounds: number;
    appId: number;
    pickName?: string;
    label: string;
};

/**
 * Resolves the day/round a successful response must be persisted to, or null when
 * the response was graded for a different submission or no round identity exists.
 * Pairing the label with its round identity atomically makes a fabricated entry
 * impossible: the response only saves where the guess was actually made.
 */
export function resolveSubmissionTarget(
    state: RoundActionState | null | undefined,
    submission: RoundSubmission | null | undefined,
): { gameDate: string; roundIndex: number; totalRounds: number; result: RoundResult } | null {
    if (!state?.ok || !state.response || !submission?.gameDate) return null;
    if (state.response.appId !== submission.appId) return null;
    return {
        gameDate: submission.gameDate,
        roundIndex: submission.roundIndex,
        totalRounds: submission.totalRounds,
        result: {
            appId: submission.appId,
            pickName: submission.pickName,
            selectedLabel: submission.label,
            actualBucket: state.response.actualBucket,
            totalReviews: state.response.totalReviews,
            correct: state.response.correct,
        },
    };
}

/**
 * Priority order for the result to display for a completed round:
 *   1. Live action state (just submitted this session)
 *   2. Stored result from localStorage or server (authenticated restore)
 *
 * Extracting this pure function makes the priority rule testable without
 * mounting the component.
 */
export function resolveEffectiveResponse(
    state: RoundActionState | null | undefined,
    storedThisRound: RoundResult | undefined,
    appId: number,
): GuessResponse | null {
    // The round page component is reused across rounds, so the action state can still hold
    // the previous round's response while this round's submit is in flight — a response
    // graded for another pick must never be shown as this round's result.
    if (state?.ok && state.response && state.response.appId === appId) return state.response;
    if (storedThisRound) {
        return {
            appId: storedThisRound.appId,
            totalReviews: storedThisRound.totalReviews,
            actualBucket: storedThisRound.actualBucket,
            correct: storedThisRound.correct,
        };
    }
    return null;
}
