import type {GuessResponse} from "@/types/review-game";
import type {RoundResult} from "@/lib/storage";

type ActionState = { ok: boolean; response?: GuessResponse };
type Prefill = { selectedLabel: string; actualBucket?: string; totalReviews?: number };

/**
 * Converts a server-prefill shape to a GuessResponse, or returns null when
 * no label is present. The `correct` flag is derived from the data rather
 * than stored, so it is always consistent.
 */
export function prefillToResponse(appId: number, prefill: Prefill | undefined): GuessResponse | null {
    if (!prefill?.selectedLabel) return null;
    return {
        appId,
        totalReviews: prefill.totalReviews ?? 0,
        actualBucket: prefill.actualBucket ?? '',
        correct: prefill.actualBucket
            ? prefill.actualBucket === prefill.selectedLabel
            : false,
    };
}

/**
 * Whether the round should render as already submitted: a successful action
 * state whose response was graded for this round's pick, or a restored result
 * (stored/server) or an authenticated prefill for this round. The round page
 * reuses its component across rounds, so a stale ok-state can still hold the
 * previous round's response while the new submit is in flight — it must never
 * mark this round submitted, or the guess buttons would render disabled with
 * no result and no error shown.
 */
export function isRoundSubmitted(
    state: ActionState | null | undefined,
    appId: number,
    storedThisRound: RoundResult | undefined,
    prefilled: boolean,
): boolean {
    return Boolean(
        (state?.ok && state.response?.appId === appId)
        || storedThisRound
        || prefilled,
    );
}

/**
 * Priority order for the result to display for a completed round:
 *   1. Live action state (just submitted this session)
 *   2. Stored result from localStorage or server (authenticated restore)
 *   3. Server-prefilled result (SSR preload)
 *
 * Extracting this pure function makes the priority rule testable without
 * mounting the component.
 */
export function resolveEffectiveResponse(
    state: ActionState | null | undefined,
    storedThisRound: RoundResult | undefined,
    prefilledResponse: GuessResponse | null,
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
    return prefilledResponse;
}
