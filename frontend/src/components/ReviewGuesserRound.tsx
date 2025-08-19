"use client";

import {useActionState, useEffect, useMemo, useState} from "react";
import type {GuessResponse} from "@/types/review-game";
import Link from "next/link";
import type {GuessActionState} from "../../app/review-guesser/[round]/actions";
import {submitGuessAction} from "../../app/review-guesser/[round]/actions";
import GuessButtons from "@/components/GuessButtons";
import RoundResult from "@/components/RoundResult";
import RoundPoints from "@/components/RoundPoints";
import RoundSummary from "@/components/RoundSummary";
import ShareControls from "@/components/ShareControls";
import {buildSteamLoginUrl} from "@/components/SteamLoginButton";
import ReviewRules from "@/components/ReviewRules";
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

type StoredDay = { totalRounds: number; results: Record<number, StoredRoundResult> };

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
    const [selectedLabel, setSelectedLabel] = useState<string | null>(null);
    const [selectionScopeKey, setSelectionScopeKey] = useState<string | null>(null);
    const [stored, setStored] = useState<StoredDay | null>(null);

    // Client-side fetch of authenticated guesses if not provided via props
    type ServerGuess = {
        roundIndex: number;
        appId: number;
        selectedBucket: string;
        actualBucket?: string;
        totalReviews?: number
    };
    const [serverGuesses, setServerGuesses] = useState<Record<number, ServerGuess>>({});
    const [fetchedPrefill, setFetchedPrefill] = useState<{
        selectedLabel: string;
        actualBucket?: string;
        totalReviews?: number
    } | undefined>(undefined);

    useEffect(() => {
        let cancelled = false;

        async function load() {
            // Only fetch if caller did not provide results/prefill
            if (prefilled || (allResults && Object.keys(allResults).length > 0)) return;
            try {
                const res = await fetch('/api/review-game/my/today', {credentials: 'include', cache: 'no-store'});
                if (!res.ok) return;
                const data = await res.json() as ServerGuess[];
                if (cancelled) return;
                const map: Record<number, ServerGuess> = {};
                for (const g of data) map[g.roundIndex] = g;
                setServerGuesses(map);
                const g = map[roundIndex];
                if (g) setFetchedPrefill({
                    selectedLabel: g.selectedBucket,
                    actualBucket: g.actualBucket ?? '',
                    totalReviews: g.totalReviews ?? 0
                });
            } catch {
                // ignore
            }
        }

        load();
        return () => {
            cancelled = true;
        };
    }, [roundIndex, prefilled, allResults]);

    const scopeKey = `${gameDate ?? ''}:${roundIndex}:${appId}`;

    // Reset any previous selection when round/app/date changes before applying stored/prefilled
    useEffect(() => {
        setSelectedLabel(null);
        setSelectionScopeKey(null);
    }, [roundIndex, appId, gameDate]);

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
        // Persist locally to ensure immediate completion UX even for authenticated users
        if (!state || !state.ok || !state.response || !selectedLabel || !gameDate) return;
        const key = `review-guesser:${gameDate}`;
        try {
            const prevRaw = window.localStorage.getItem(key);
            let data: StoredDay = {totalRounds, results: {}};
            if (prevRaw) {
                const parsed = JSON.parse(prevRaw) as unknown;
                if (
                    typeof parsed === 'object' && parsed !== null &&
                    'results' in (parsed as Record<string, unknown>)
                ) {
                    data = parsed as StoredDay;
                }
            }
            data.totalRounds = totalRounds;
            data.results[roundIndex] = {
                appId,
                pickName,
                selectedLabel,
                actualBucket: state.response.actualBucket,
                totalReviews: state.response.totalReviews,
                correct: state.response.correct,
            };
            window.localStorage.setItem(key, JSON.stringify(data));
            setStored(data);
        } catch {
            // ignore storage errors
        }
    }, [state, selectedLabel, gameDate, totalRounds, roundIndex, appId, pickName, prefilled]);

    // Restore previously submitted guess for this round and load stored day once on mount/date change
    useEffect(() => {
        // Initialize from server-provided prefilled guess if present (without writing to localStorage)
        const computedPrefill = prefilled ?? fetchedPrefill;
        if (computedPrefill && computedPrefill.selectedLabel && !selectedLabel) {
            setSelectedLabel(computedPrefill.selectedLabel);
            setSelectionScopeKey(scopeKey);
        }
        if (!gameDate) return;
        try {
            const raw = window.localStorage.getItem(`review-guesser:${gameDate}`);
            if (!raw) {
                setStored(null);
                return;
            }
            const data = JSON.parse(raw) as StoredDay;
            setStored(data);
            const existing = data.results?.[roundIndex];
            if (existing && existing.selectedLabel) {
                setSelectedLabel(existing.selectedLabel);
                setSelectionScopeKey(scopeKey);
            }
        } catch {
            setStored(null);
        }
    }, [gameDate, roundIndex, prefilled, totalRounds, appId, pickName, selectedLabel, scopeKey]);

    // Determine completion and existing result for this round
    const storedResults = stored?.results || {};
    const serverResults = (allResults && Object.keys(allResults).length > 0)
        ? allResults
        : Object.fromEntries(Object.entries(serverGuesses).map(([k, v]) => [Number(k), {
            appId: v.appId,
            pickName: undefined,
            selectedLabel: v.selectedBucket,
            actualBucket: v.actualBucket ?? '',
            totalReviews: v.totalReviews ?? 0,
            correct: v.actualBucket ? (v.actualBucket === v.selectedBucket) : false,
        }])) as Record<number, StoredRoundResult>;
    const storedThisRound = storedResults[roundIndex] || serverResults[roundIndex];

    // Prefer server response; fallback to stored round result; finally use prefilled from server (authenticated restore)
    const computedPrefill = prefilled ?? fetchedPrefill;
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
    const canShowShare = Boolean(
        effectiveResponse ||
        storedThisRound ||
        Object.keys(serverResults).length > 0
    );

    // Determine auth state (client-side) to conditionally show the sign-in nudge
    const [signedIn, setSignedIn] = useState<boolean | null>(null);
    useEffect(() => {
        let cancelled = false;

        async function check() {
            try {
                const res = await fetch('/api/auth/me', {cache: 'no-store'});
                const json = await res.json();
                if (!cancelled) setSignedIn(Boolean(json?.signedIn));
            } catch {
                if (!cancelled) setSignedIn(false);
            }
        }

        check();
        return () => {
            cancelled = true;
        };
    }, []);

    return (
        <>
            <h2>Submit Your Guess</h2>
            <GuessButtons
                appId={appId}
                buckets={buckets}
                bucketTitles={bucketTitles}
                selectedLabel={renderSelectedLabel}
                onSelect={setSelectedLabel}
                submitted={submittedFlag}
                formAction={formAction as unknown as (formData: FormData) => void}
            />

            {state && !state.ok && state.error && (
                <p className="text-muted review-round__error">Error: {state.error}</p>
            )}

            {(effectiveResponse || prefilled) && (
                <div role="dialog" aria-modal="true" className="review-round__result">
                    <RoundResult result={(effectiveResponse ?? {
                        appId,
                        totalReviews: computedPrefill?.totalReviews ?? 0,
                        actualBucket: computedPrefill?.actualBucket ?? '',
                        correct: computedPrefill?.actualBucket ? (computedPrefill.actualBucket === (computedPrefill?.selectedLabel ?? '')) : false,
                    }) as GuessResponse}/>
                    <RoundPoints
                        buckets={buckets}
                        selectedLabel={storedThisRound?.selectedLabel ?? renderSelectedLabel}
                        actualBucket={(effectiveResponse ?? {actualBucket: computedPrefill?.actualBucket ?? ''} as GuessResponse).actualBucket}
                    />
                    {/* Summary rendered below to avoid duplication and keep actions visible */}
                    <div className="review-round__actions">
                        <a
                            href={`https://store.steampowered.com/app/${appId}`}
                            target="_blank"
                            rel="noopener noreferrer"
                            className="btn-ghost"
                            aria-label="Open this game on Steam"
                        >
                            Open on Steam ↗
                        </a>
                        {prevHref && (
                            <Link href={prevHref} className="btn-ghost" aria-label="Go to previous round">← Previous
                                round</Link>
                        )}
                        {roundIndex < totalRounds && (
                            <Link href={nextHref} className="btn-cta" aria-label="Go to next round">Next round →</Link>
                        )}
                        {canShowShare && (
                            <>
                                {/* Inline nudge only for guests */}
                                {signedIn === false && (
                                    <p className="text-muted" style={{margin: '0.5rem 0 0.25rem 0'}}>
                                        <a href="#" onClick={(e) => {
                                            e.preventDefault();
                                            window.location.href = buildSteamLoginUrl();
                                        }}>Sign in with Steam</a>
                                        &nbsp;to save your results, track streaks, and appear on the leaderboard.
                                    </p>
                                )}
                                <ShareControls
                                    inline
                                    buckets={buckets}
                                    gameDate={gameDate}
                                    totalRounds={totalRounds}
                                    latestRound={latestStoredRoundIndex}
                                    latest={latestStored ? latestStored : {
                                        appId,
                                        pickName,
                                        selectedLabel: selectedLabel ?? '',
                                        actualBucket: effectiveResponse ? effectiveResponse.actualBucket : '',
                                        totalReviews: effectiveResponse ? effectiveResponse.totalReviews : 0,
                                        correct: effectiveResponse ? effectiveResponse.correct : false,
                                    }}
                                    results={Object.keys(serverResults).length > 0 ? serverResults : undefined}
                                />
                            </>
                        )}
                    </div>
                    {canShowShare && (
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
                            results={Object.keys(serverResults).length > 0 ? serverResults : undefined}
                        />
                    )}
                </div>
            )}

            {!signedIn && roundIndex === 1 && <ReviewRules/>}
        </>
    );
}
