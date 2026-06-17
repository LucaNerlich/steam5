"use client";

import {useActionState, useEffect, useMemo, useState, useTransition} from "react";
import type {GuessResponse} from "@/types/review-game";
import type {GuessActionState} from "../../app/review-guesser/[round]/actions";
import {submitGuessAction} from "../../app/review-guesser/[round]/actions";
import GuessButtons from "@/components/GuessButtons";
import AuthWarningModal from "@/components/AuthWarningModal";
import RoundResultDialog from "@/components/RoundResultDialog";
import RoundResultActions from "@/components/RoundResultActions";
import ShareControls from "@/components/ShareControls";
import RoundSummary from "@/components/RoundSummary";
import {buildSteamLoginUrl} from "@/components/SteamLoginButton";
import {useAuth} from "@/contexts/AuthContext";
import useServerGuesses from "@/lib/hooks/useServerGuesses";
import useRoundArrowNavigation from "@/lib/hooks/useRoundArrowNavigation";
import {loadDay, saveRound, type StoredDay, type RoundResult} from "@/lib/storage";
import {prefillToResponse, resolveEffectiveResponse} from "@/lib/guessResolution";
import {computeSignedOutDuringPlay, resolveLiveSignedIn, shouldWarnBeforeSubmit} from "@/lib/authGuard";
import "@/styles/components/reviewGuesserRound.css";
import "@/styles/components/reviewRoundResult.css";
import "@/styles/components/reviewShareControls.css";

interface Props {
    appId: number;
    buckets: string[];
    bucketTitles?: string[];
    roundIndex: number;
    totalRounds: number;
    pickName?: string;
    gameDate?: string;
    prefilled?: { selectedLabel: string; actualBucket?: string; totalReviews?: number };
    allResults?: Record<number, {
        appId: number;
        pickName?: string;
        selectedLabel: string;
        actualBucket: string;
        totalReviews: number;
        correct: boolean;
    }>;
}

type StoredRoundResult = RoundResult;

// Persisted preference: when set, the "not signed in" warning is suppressed on
// every round so the user is not nagged on each submit.
const AUTH_WARNING_DISMISSED_COOKIE = "s5_auth_warning_dismissed";

function hasDismissedAuthWarning(): boolean {
    if (typeof document === "undefined") return false;
    return document.cookie.split("; ").some((c) => c === `${AUTH_WARNING_DISMISSED_COOKIE}=1`);
}

function dismissAuthWarning(): void {
    if (typeof document === "undefined") return;
    const oneYear = 365 * 24 * 60 * 60;
    document.cookie = `${AUTH_WARNING_DISMISSED_COOKIE}=1; path=/; max-age=${oneYear}; SameSite=Lax`;
}

// Freshly verify the session at submit time. The cached signedIn flag can be
// stale (the s5_token cookie may have been dropped mid-session), and because that
// cookie is HttpOnly the client cannot inspect it directly — only the server can
// tell us. Errors are treated as "still signed in" so a transient failure never
// blocks a guess; the post-submit persisted check is the backstop.
async function fetchSignedIn(): Promise<boolean> {
    try {
        const r = await fetch('/api/auth/me', {cache: 'no-store'});
        if (r.status === 401) return false;
        if (!r.ok) return true;
        const data = await r.json();
        return Boolean(data?.signedIn);
    } catch {
        return true;
    }
}

export default function ReviewGuesserRound({
                                               appId,
                                               buckets,
                                               bucketTitles,
                                               roundIndex,
                                               totalRounds,
                                               pickName,
                                               gameDate,
                                               prefilled,
                                               allResults
                                           }: Props) {
    const initial: GuessActionState = {ok: false};
    const [state, formAction] = useActionState<GuessActionState, FormData>(submitGuessAction, initial);
    const [isPending, startTransition] = useTransition();
    const [selectedLabel, setSelectedLabel] = useState<string | null>(null);
    const [showAuthWarning, setShowAuthWarning] = useState(false);
    const [pendingFormData, setPendingFormData] = useState<FormData | null>(null);
    const [selectionScopeKey, setSelectionScopeKey] = useState<string | null>(null);
    // Read this day's saved results synchronously so a same-device returning
    // player's result is known on the first render — no guess-card flash before
    // localStorage is consulted.
    const [stored, setStored] = useState<StoredDay | null>(() => {
        if (typeof window === "undefined" || !gameDate) return null;
        return loadDay(gameDate);
    });
    const disableClientFetch = Boolean(prefilled) || (allResults && Object.keys(allResults).length >= totalRounds);
    const {guesses: serverGuesses, loading: serverGuessesLoading} = useServerGuesses(disableClientFetch);

    // The slot shows a placeholder until we've resolved whether this round is
    // already answered, so the guess card never flashes before swapping to the
    // result. Flipped after mount once client-only state (localStorage) is read.
    const [mounted, setMounted] = useState(false);
    useEffect(() => {
        setMounted(true);
    }, []);

    const scopeKey = `${gameDate ?? ''}:${roundIndex}:${appId}`;

    // @view-transition { navigation: auto } in globals.css overrides Next.js's
    // default scroll-to-top on client navigations, so we reset manually.
    useEffect(() => {
        window.scrollTo({ top: 0, behavior: "instant" });
    }, [roundIndex]);

    // Derive a reset signal from scope changes instead of using an effect
    const [prevScopeKey, setPrevScopeKey] = useState<string | null>(null);
    if (prevScopeKey !== scopeKey) {
        setPrevScopeKey(scopeKey);
        if (selectedLabel !== null) setSelectedLabel(null);
        if (selectionScopeKey !== null) setSelectionScopeKey(null);
    }

    const nextHref = useMemo(() => {
        const next = roundIndex + 1;
        return next <= totalRounds ? `/review-guesser/${next}` : `/review-guesser/1`;
    }, [roundIndex, totalRounds]);

    const prevHref = useMemo(() => {
        const prev = roundIndex - 1;
        return prev >= 1 ? `/review-guesser/${prev}` : null;
    }, [roundIndex]);
    const hasNextRound = roundIndex < totalRounds;
    useRoundArrowNavigation({prevHref, nextHref, hasNextRound, disabled: showAuthWarning});

    // Persist this round's result for the current game date
    useEffect(() => {
        if (!state || !state.ok || !state.response || !selectedLabel || !gameDate) return;
        const updated = saveRound(gameDate, roundIndex, totalRounds, {
            appId,
            pickName,
            selectedLabel,
            actualBucket: state.response.actualBucket,
            totalReviews: state.response.totalReviews,
            correct: state.response.correct,
        });
        if (updated) setStored(updated);
    }, [state, selectedLabel, gameDate, totalRounds, roundIndex, appId, pickName, prefilled]);

    // Restore previously submitted guess for this round and load stored day once on mount/date change
    useEffect(() => {
        // Initialize from server-provided prefilled guess if present (without writing to localStorage)
        const maybeServerPrefill = (() => {
            if (prefilled) return prefilled;
            const g = serverGuesses[roundIndex];
            // Ignore a guess that belongs to a different pick: results are keyed by
            // round, but only valid when the appId still matches the current pick
            // (today's picks may have been regenerated since the guess was made).
            if (!g || g.appId !== appId) return undefined;
            return {
                selectedLabel: g.selectedLabel,
                actualBucket: g.actualBucket,
                totalReviews: g.totalReviews,
            };
        })();
        if (maybeServerPrefill && maybeServerPrefill.selectedLabel && !selectedLabel) {
            setSelectedLabel(maybeServerPrefill.selectedLabel);
            setSelectionScopeKey(scopeKey);
        }
        if (!gameDate) return;
        const data = loadDay(gameDate);
        if (!data) {
            setStored(null);
            return;
        }
        setStored(data);
        const existing = data.results?.[roundIndex];
        if (existing && existing.selectedLabel && existing.appId === appId) {
            setSelectedLabel(existing.selectedLabel);
            setSelectionScopeKey(scopeKey);
        }
    }, [gameDate, roundIndex, prefilled, serverGuesses, totalRounds, appId, pickName, selectedLabel, scopeKey]);

    // Auth state drives which source of truth decides a round's result. Computed
    // before the result selection below so it can pick the authoritative source.
    // `signedIn` is null while auth is still loading.
    const {isSignedIn, isLoading: authLoading, refreshAuth} = useAuth();
    const signedIn = authLoading ? null : isSignedIn;

    // Determine completion and existing result for this round.
    // serverGuesses already arrives in StoredRoundResult shape from the hook.
    const storedResults = stored?.results || {};
    const serverResults: Record<number, StoredRoundResult> = {
        ...serverGuesses,
        ...(allResults ?? {}),
    };
    // A stored/server result is only valid for this round if it belongs to the
    // current pick. When today's picks are regenerated, results keyed by round
    // index would otherwise surface a previous game's guess on the new game.
    const isForCurrentPick = (r?: StoredRoundResult) => !!r && r.appId === appId;
    const localThisRound = isForCurrentPick(storedResults[roundIndex]) ? storedResults[roundIndex] : undefined;
    const serverThisRound = isForCurrentPick(serverResults[roundIndex]) ? serverResults[roundIndex] : undefined;
    // Source of truth: for a signed-in player the server (/my/today) is
    // authoritative — only a result the backend actually persisted counts, so a
    // localStorage entry (e.g. a guess made while logged out, then a login) must
    // not mark the round answered and hide the guess card. An anonymous player has
    // no server record, so their localStorage entry is authoritative. A live
    // just-submitted response still wins, via resolveEffectiveResponse below.
    const storedThisRound = signedIn === false ? (localThisRound ?? serverThisRound) : serverThisRound;

    // Prefer server response; fallback to stored round result; finally use prefilled from server (authenticated restore)
    const computedPrefill = useMemo(() => {
        if (prefilled) return prefilled;
        const g = serverGuesses[roundIndex];
        if (!g || g.appId !== appId) return undefined;
        return {
            selectedLabel: g.selectedLabel,
            actualBucket: g.actualBucket,
            totalReviews: g.totalReviews,
        };
    }, [prefilled, serverGuesses, roundIndex, appId]);
    const prefilledResponse = prefillToResponse(appId, computedPrefill);
    const effectiveResponse = resolveEffectiveResponse(state, storedThisRound, prefilledResponse);

    // Whether this round is already answered, from the authoritative source.
    const hasResult = Boolean(effectiveResponse || storedThisRound || prefilled);
    // Gate the slot's content until we can commit to result-vs-guess-card without
    // a visible swap. We wait for mount (client state available) and for auth to
    // resolve. For a signed-in player we additionally wait for /my/today to settle
    // so the server — their source of truth — decides which result is shown. An
    // anonymous player resolves instantly from localStorage. `mounted` persists
    // across round navigation, so the placeholder only shows on the first load.
    const resolved = mounted
        && signedIn !== null
        && (signedIn === false || !serverGuessesLoading);

    // Effective selected label used for rendering (ignore stale selection from previous scope)
    const renderSelectedLabel = selectionScopeKey === scopeKey ? selectedLabel : (computedPrefill?.selectedLabel ?? null);

    // Merge the "current" submitted/effective response into server results so
    // authenticated users see completion immediately without relying on SSR re-fetch
    const mergedServerResults: Record<number, StoredRoundResult> = {...serverResults};
    if (effectiveResponse) {
        mergedServerResults[roundIndex] = {
            appId,
            pickName, // preserve current round's name
            selectedLabel: storedThisRound?.selectedLabel ?? (renderSelectedLabel ?? ''),
            actualBucket: effectiveResponse.actualBucket,
            totalReviews: effectiveResponse.totalReviews,
            correct: effectiveResponse.correct,
        };
    }

    const latestStoredRoundIndex = (() => {
        const keysLocal = Object.keys(storedResults).map(n => parseInt(n, 10)).filter(Number.isFinite);
        const keysServer = Object.keys(mergedServerResults).map(n => parseInt(n, 10)).filter(Number.isFinite);
        const keys = [...keysLocal, ...keysServer];
        if (keys.length === 0) return roundIndex;
        return Math.max(...keys);
    })();
    const latestStored = storedResults[latestStoredRoundIndex] || mergedServerResults[latestStoredRoundIndex];

    // Submitted flag: either current state submitted, or restored from storage, or authenticated prefilled for this round
    const submittedFlag = Boolean(state && (state.ok || state.error)) || Boolean(storedThisRound) || Boolean(prefilled);

    // Single source of truth: when to show ShareControls
    const hasServerResults = Object.keys(serverResults).length > 0;
    const canShowShare = Boolean(
        effectiveResponse ||
        storedThisRound ||
        (!serverGuessesLoading && hasServerResults)
    );

    // Detect a silently-lost session: either the backend rejected our cookie (401),
    // or the UI believed we were signed in but the guess was saved anonymously
    // (cookie was dropped while the SPA stayed open). In both cases the result did
    // not count, so re-sync the auth state and surface a clear notice.
    const signedOutDuringPlay = computeSignedOutDuringPlay(signedIn, state);
    useEffect(() => {
        if (signedOutDuringPlay) refreshAuth();
    }, [signedOutDuringPlay, refreshAuth]);

    const cloneFormData = (formData: FormData) => {
        const copy = new FormData();
        formData.forEach((value, key) => {
            copy.append(key, value);
        });
        return copy;
    };

    const submitGuess = (formData: FormData) => {
        startTransition(() => {
            formAction(formData);
        });
    };

    const handleAuthGuardedSubmit = async (formData: FormData) => {
        const dismissed = hasDismissedAuthWarning();
        // Determine the live signed-in state. signedIn === false is reliable
        // (set from the server on load), so trust it directly; a dismissed user
        // never needs the check. Otherwise the cached value may be stale, so
        // re-validate before letting the guess count — this catches a session
        // lost mid-round (the HttpOnly s5_token cookie can vanish without the
        // client knowing).
        const fetched = (dismissed || signedIn === false) ? false : await fetchSignedIn();
        const live = resolveLiveSignedIn(signedIn, fetched);
        if (shouldWarnBeforeSubmit(dismissed, live)) {
            if (signedIn !== false) refreshAuth(); // sync header with the fresh value
            setPendingFormData(cloneFormData(formData));
            setShowAuthWarning(true);
            return;
        }
        submitGuess(formData);
    };

    const handleLogin = () => {
        setShowAuthWarning(false);
        setPendingFormData(null);
        window.location.href = buildSteamLoginUrl();
    };

    const handleSkip = (reason?: "backdrop" | "button" | "escape") => {
        setShowAuthWarning(false);
        if (reason === "backdrop") return;
        if (pendingFormData) {
            const data = pendingFormData;
            setPendingFormData(null);
            submitGuess(data);
        }
    };

    const handleIgnore = () => {
        dismissAuthWarning();
        setShowAuthWarning(false);
        if (pendingFormData) {
            const data = pendingFormData;
            setPendingFormData(null);
            submitGuess(data);
        }
    };

    const shouldShowGuessControls = resolved && !hasResult;
    return (
        <>
            <div className="review-round__slot">
            {!resolved && (
                // Neutral placeholder while we resolve result-vs-guess-card, so the
                // guess card never flashes before the result swaps in. Sits inside
                // the height-reserved slot, so nothing below reflows.
                <div className="review-round__skeleton" aria-hidden="true"/>
            )}
            {shouldShowGuessControls && (
                <section className="review-round__guess-card" aria-labelledby="guess-submission">
                    <div className="review-round__guess-header">
                        <h2 id="guess-submission">Submit Your Guess</h2>
                    </div>
                    <GuessButtons
                        appId={appId}
                        buckets={buckets}
                        bucketTitles={bucketTitles}
                        selectedLabel={renderSelectedLabel}
                        onSelect={setSelectedLabel}
                        submitted={submittedFlag}
                        isPending={isPending}
                        formAction={handleAuthGuardedSubmit}
                        helperText="Pick the review bucket that best matches this game."
                        prefetchHref={hasNextRound ? nextHref : null}
                    />
                    {signedOutDuringPlay ? (
                        <p className="text-muted review-round__error">
                            You&apos;ve been signed out, so this result wasn&apos;t saved.{" "}
                            <button type="button" className="btn-link" onClick={() => {
                                window.location.href = buildSteamLoginUrl();
                            }}>Sign in with Steam</button>
                            &nbsp;to save your results.
                        </p>
                    ) : state && !state.ok && state.error && (
                        <p className="text-muted review-round__error">Error: {state.error}</p>
                    )}
                </section>
            )}

            {(effectiveResponse || prefilled) && (
                <RoundResultDialog
                    buckets={buckets}
                    selectedLabel={storedThisRound?.selectedLabel ?? renderSelectedLabel ?? (computedPrefill?.selectedLabel ?? null)}
                    result={(effectiveResponse ?? {
                        appId,
                        totalReviews: computedPrefill?.totalReviews ?? 0,
                        actualBucket: computedPrefill?.actualBucket ?? '',
                        correct: computedPrefill?.actualBucket ? (computedPrefill.actualBucket === (computedPrefill?.selectedLabel ?? '')) : false,
                    }) as GuessResponse}
                >
                    <RoundResultActions
                        appId={appId}
                        prevHref={prevHref}
                        nextHref={roundIndex < totalRounds ? nextHref : null}
                    >
                        {canShowShare && (
                            <>
                                <ShareControls
                                    inline
                                    buckets={buckets}
                                    gameDate={gameDate}
                                    totalRounds={totalRounds}
                                    latestRound={latestStoredRoundIndex}
                                    latest={(Object.keys(mergedServerResults).length > 0 ? mergedServerResults[latestStoredRoundIndex] : null) ||
                                        latestStored ||
                                        {
                                            appId,
                                            pickName,
                                            selectedLabel: (storedThisRound?.selectedLabel ?? renderSelectedLabel ?? '') as string,
                                            actualBucket: effectiveResponse ? effectiveResponse.actualBucket : (storedThisRound?.actualBucket ?? ''),
                                            totalReviews: effectiveResponse ? effectiveResponse.totalReviews : (storedThisRound?.totalReviews ?? 0),
                                            correct: effectiveResponse ? effectiveResponse.correct : (storedThisRound?.correct ?? false),
                                        }
                                    }
                                    results={!serverGuessesLoading && hasServerResults ? serverResults : undefined}
                                    signedIn={signedIn}
                                />
                                <RoundSummary
                                    buckets={buckets}
                                    gameDate={gameDate}
                                    totalRounds={totalRounds}
                                    latestRound={latestStoredRoundIndex}
                                    latest={(Object.keys(mergedServerResults).length > 0 ? mergedServerResults[latestStoredRoundIndex] : null) ||
                                        latestStored ||
                                        {
                                            appId,
                                            pickName,
                                            selectedLabel: (storedThisRound?.selectedLabel ?? renderSelectedLabel ?? '') as string,
                                            actualBucket: effectiveResponse ? effectiveResponse.actualBucket : (storedThisRound?.actualBucket ?? ''),
                                            totalReviews: effectiveResponse ? effectiveResponse.totalReviews : (storedThisRound?.totalReviews ?? 0),
                                            correct: effectiveResponse ? effectiveResponse.correct : (storedThisRound?.correct ?? false),
                                        }
                                    }
                                    results={!serverGuessesLoading && hasServerResults ? serverResults : undefined}
                                />
                                {signedOutDuringPlay ? (
                                    <p className="text-muted review-round__signin-nudge">
                                        You&apos;ve been signed out, so this result wasn&apos;t saved.{" "}
                                        <button type="button" className="btn-link" onClick={() => {
                                            window.location.href = buildSteamLoginUrl();
                                        }}>Sign in with Steam</button>
                                        &nbsp;to save your results, track streaks, and appear on the leaderboard.
                                    </p>
                                ) : signedIn === false && (
                                    <p className="text-muted review-round__signin-nudge">
                                        <button type="button" className="btn-link" onClick={() => {
                                            window.location.href = buildSteamLoginUrl();
                                        }}>Sign in with Steam</button>
                                        &nbsp;to save your results, track streaks, and appear on the leaderboard.
                                    </p>
                                )}
                            </>
                        )}
                    </RoundResultActions>
                </RoundResultDialog>
            )}
            </div>

            <AuthWarningModal
                isOpen={showAuthWarning}
                onLogin={handleLogin}
                onSkip={handleSkip}
                onIgnore={handleIgnore}
            />
        </>
    );
}
