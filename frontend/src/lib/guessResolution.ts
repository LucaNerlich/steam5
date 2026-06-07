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
): GuessResponse | null {
    if (state?.ok && state.response) return state.response;
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
