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
import ReviewRules from "@/components/ReviewRules";
import "@/styles/components/reviewRoundResult.css";
import "@/styles/components/reviewShareControls.css";

interface Props {
    appId: number;
    buckets: string[];
    roundIndex: number;
    totalRounds: number;
    pickName?: string;
    gameDate?: string;
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

export default function ReviewGuesserRound({appId, buckets, roundIndex, totalRounds, pickName, gameDate}: Props) {
    const initial: GuessActionState = {ok: false};
    const [state, formAction] = useActionState<GuessActionState, FormData>(submitGuessAction, initial);
    const [selectedLabel, setSelectedLabel] = useState<string | null>(null);
    const [stored, setStored] = useState<StoredDay | null>(null);

    const nextHref = useMemo(() => {
        const next = roundIndex + 1;
        return next <= totalRounds ? `/review-guesser/${next}` : `/review-guesser/1`;
    }, [roundIndex, totalRounds]);

    // Persist this round's result for the current game date
    useEffect(() => {
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
    }, [state, selectedLabel, gameDate, totalRounds, roundIndex, appId, pickName]);

    // Restore previously submitted guess for this round and load stored day once on mount/date change
    useEffect(() => {
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
            }
        } catch {
            setStored(null);
        }
    }, [gameDate, roundIndex]);

    // Determine completion and existing result for this round
    const storedResults = stored?.results || {};
    const storedThisRound = storedResults[roundIndex];
    const completedCount = Object.keys(storedResults).length;
    const isComplete = completedCount >= totalRounds;
    const latestStoredRoundIndex = (() => {
        const keys = Object.keys(storedResults).map(n => parseInt(n, 10)).filter(Number.isFinite);
        if (keys.length === 0) return roundIndex;
        return Math.max(...keys);
    })();
    const latestStored = storedResults[latestStoredRoundIndex];

    // Prefer server response; fallback to stored round result for showing the dialog
    const effectiveResponse: GuessResponse | null = state && state.ok && state.response
        ? state.response
        : storedThisRound
            ? {
                appId: storedThisRound.appId,
                totalReviews: storedThisRound.totalReviews,
                actualBucket: storedThisRound.actualBucket,
                correct: storedThisRound.correct
            }
            : null;

    // Submitted flag: either current state submitted or we restored a previous submission
    const submittedFlag = Boolean(state && (state.ok || state.error)) || Boolean(storedThisRound);

    if (isComplete && roundIndex >= totalRounds) {
        // Only show share when the day is complete
        return (
            <div>
                <div role="dialog" aria-modal="true" className="review-round__result">
                    <RoundResult result={(effectiveResponse ?? {
                        appId,
                        totalReviews: storedThisRound?.totalReviews ?? 0,
                        actualBucket: storedThisRound?.actualBucket ?? '',
                        correct: storedThisRound?.correct ?? false,
                    }) as GuessResponse}/>
                    <RoundPoints
                        buckets={buckets}
                        selectedLabel={storedThisRound?.selectedLabel ?? selectedLabel}
                        actualBucket={(effectiveResponse ?? {
                            actualBucket: storedThisRound?.actualBucket ?? ''
                        } as GuessResponse).actualBucket}
                    />
                    <RoundSummary
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
                    />
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
                        />
                    </div>
                </div>
                <p className="review-round__complete text-muted">You have completed all rounds for today.</p>
            </div>
        );
    }

    return (
        <>
            <h3>Review Count Guess</h3>
            <GuessButtons
                appId={appId}
                buckets={buckets}
                selectedLabel={selectedLabel}
                onSelect={setSelectedLabel}
                submitted={submittedFlag}
                formAction={formAction as unknown as (formData: FormData) => void}
            />

            {state && !state.ok && state.error && (
                <p className="text-muted review-round__error">Error: {state.error}</p>
            )}

            {effectiveResponse && (
                <div role="dialog" aria-modal="true" className="review-round__result">
                    <RoundResult result={effectiveResponse}/>
                    <RoundPoints
                        buckets={buckets}
                        selectedLabel={storedThisRound?.selectedLabel ?? selectedLabel}
                        actualBucket={effectiveResponse.actualBucket}
                    />
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
                        {roundIndex < totalRounds && (
                            <Link href={nextHref} className="btn-cta" aria-label="Go to next round">Next round →</Link>
                        )}
                    </div>
                </div>
            )}
            {isComplete && (
                <div className="review-round__share">
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
                    />
                </div>
            )}
            {!isComplete && <ReviewRules/>}
        </>
    );
}
