"use client";

import {useActionState, useEffect, useMemo, useState, useTransition} from "react";
import type {GuessResponse} from "@/types/review-game";
import type {GuessActionState} from "../../app/review-guesser/[round]/actions";
import {submitGuessAction} from "../../app/review-guesser/[round]/actions";
import GuessButtons from "@/components/GuessButtons";
import AuthWarningModal from "@/components/AuthWarningModal";
import RoundResultDialog from "@/components/RoundResultDialog";
import RoundResultActions from "@/components/RoundResultActions";
import RoundShareSummary from "@/components/RoundShareSummary";
import {buildSteamLoginUrl} from "@/components/SteamLoginButton";
import useAuthSignedIn from "@/lib/hooks/useAuthSignedIn";
import useServerGuesses from "@/lib/hooks/useServerGuesses";
import {loadDay, saveRound, type StoredDay} from "@/lib/storage";
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

type StoredRoundResult = {
    appId: number;
    pickName?: string;
    selectedLabel: string;
    actualBucket: string;
    totalReviews: number;
    correct: boolean;
};

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
    const [stored, setStored] = useState<StoredDay | null>(null);
    const disableClientFetch = Boolean(prefilled) || (allResults && Object.keys(allResults).length >= totalRounds);
    const {guesses: serverGuesses, loading: serverGuessesLoading} = useServerGuesses(disableClientFetch);

    const scopeKey = `${gameDate ?? ''}:${roundIndex}:${appId}`;

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
            if (!g) return undefined;
            return {
                selectedLabel: g.selectedBucket,
                actualBucket: g.actualBucket ?? '',
                totalReviews: g.totalReviews ?? 0,
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
        if (existing && existing.selectedLabel) {
            setSelectedLabel(existing.selectedLabel);
            setSelectionScopeKey(scopeKey);
        }
    }, [gameDate, roundIndex, prefilled, serverGuesses, totalRounds, appId, pickName, selectedLabel, scopeKey]);

    // Determine completion and existing result for this round
    const storedResults = stored?.results || {};
    const clientResults = Object.fromEntries(Object.entries(serverGuesses).map(([k, v]) => [Number(k), {
        appId: v.appId,
        pickName: undefined,
        selectedLabel: v.selectedBucket,
        actualBucket: v.actualBucket ?? '',
        totalReviews: v.totalReviews ?? 0,
        correct: v.actualBucket ? (v.actualBucket === v.selectedBucket) : false,
    }])) as Record<number, StoredRoundResult>;
    const serverResults: Record<number, StoredRoundResult> = {
        ...clientResults,
        ...(allResults ?? {}),
    };
    const storedThisRound = storedResults[roundIndex] || serverResults[roundIndex];

    // Prefer server response; fallback to stored round result; finally use prefilled from server (authenticated restore)
    const computedPrefill = useMemo(() => {
        if (prefilled) return prefilled;
        const g = serverGuesses[roundIndex];
        if (!g) return undefined;
        return {
            selectedLabel: g.selectedBucket,
            actualBucket: g.actualBucket ?? '',
            totalReviews: g.totalReviews ?? 0,
        };
    }, [prefilled, serverGuesses, roundIndex]);
    const prefilledResponse: GuessResponse | null = computedPrefill && computedPrefill.selectedLabel
        ? {
            appId,
            totalReviews: computedPrefill.totalReviews ?? 0,
            actualBucket: computedPrefill.actualBucket ?? '',
            correct: computedPrefill.actualBucket ? (computedPrefill.actualBucket === computedPrefill.selectedLabel) : false,
        }
        : null;
    const effectiveResponse: GuessResponse | null = state && state.ok && state.response
        ? state.response
        : storedThisRound
            ? {
                appId: storedThisRound.appId,
                totalReviews: storedThisRound.totalReviews,
                actualBucket: storedThisRound.actualBucket,
                correct: storedThisRound.correct
            }
            : prefilledResponse;

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

    // Determine auth state (client-side) to conditionally show the sign-in nudge
    const signedIn = useAuthSignedIn();

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

    const handleAuthGuardedSubmit = (formData: FormData) => {
        if (roundIndex === 1 && signedIn === false) {
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

    const shouldShowGuessControls = !(effectiveResponse || storedThisRound || prefilled);
    return (
        <>
            {shouldShowGuessControls && (
                <>
                    <h2 id="guess-submission">Submit Your Guess</h2>
                    <GuessButtons
                        appId={appId}
                        buckets={buckets}
                        bucketTitles={bucketTitles}
                        selectedLabel={renderSelectedLabel}
                        onSelect={setSelectedLabel}
                        submitted={submittedFlag}
                        isPending={isPending}
                        formAction={handleAuthGuardedSubmit}
                    />
                    {state && !state.ok && state.error && (
                        <p className="text-muted review-round__error">Error: {state.error}</p>
                    )}
                </>
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
                                <RoundShareSummary
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
                                {signedIn === false && (
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

            <AuthWarningModal
                isOpen={showAuthWarning}
                onLogin={handleLogin}
                onSkip={handleSkip}
            />
        </>
    );
}
