/**
 * Pure decision helpers for the "are you really signed in?" guard around guess
 * submission. Extracted from ReviewGuesserRound so the rules can be unit-tested
 * without mounting the component (same rationale as guessResolution.ts).
 */

export type GuessOutcome = {
    ok?: boolean;
    unauthorized?: boolean;
    persisted?: boolean;
};

/**
 * True when the player appeared signed in but their guess did not count — either
 * the backend rejected the session cookie (401, `unauthorized`) or the guess was
 * saved anonymously despite the UI showing them as signed in (`persisted === false`
 * while `signedIn === true`, i.e. the HttpOnly s5_token cookie was dropped
 * mid-round). Used to surface the "you've been signed out" notice and re-sync auth.
 */
export function computeSignedOutDuringPlay(
    signedIn: boolean | null,
    state: GuessOutcome | null | undefined,
): boolean {
    if (!state) return false;
    if (state.unauthorized) return true;
    return signedIn === true && Boolean(state.ok) && state.persisted === false;
}

/**
 * Effective signed-in state to enforce at guess submit time. A cached `false` is
 * authoritative (the server sets it on load), so trust it directly; otherwise
 * defer to the freshly fetched value, because a cached `true` may be stale — the
 * HttpOnly s5_token cookie can be removed mid-session without the client knowing.
 */
export function resolveLiveSignedIn(
    cachedSignedIn: boolean | null,
    fetchedSignedIn: boolean,
): boolean {
    return cachedSignedIn === false ? false : fetchedSignedIn;
}

/**
 * Whether the not-signed-in warning modal should block this submit. The modal is
 * suppressed when the user has dismissed it ("Ignore this warning") regardless of
 * auth state; otherwise it shows whenever the effective signed-in state is false.
 */
export function shouldWarnBeforeSubmit(
    dismissed: boolean,
    liveSignedIn: boolean,
): boolean {
    if (dismissed) return false;
    return liveSignedIn === false;
}
