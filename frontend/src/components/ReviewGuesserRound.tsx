"use client";

import {useActionState, useEffect, useMemo, useState} from "react";
import type {GuessResponse} from "@/types/review-game";
import Link from "next/link";
import Form from "next/form";
import {useFormStatus} from "react-dom";
import type {GuessActionState} from "../../app/review-guesser/[round]/actions";
import {submitGuessAction} from "../../app/review-guesser/[round]/actions";
import "@/styles/components/reviewGuesserRound.css";

interface Props {
    appId: number;
    buckets: string[];
    roundIndex: number;
    totalRounds: number;
    pickName?: string;
    gameDate?: string;
}

function BucketButton({label, selectedLabel, onSelect, submitted}: {
    label: string;
    selectedLabel: string | null;
    onSelect: (label: string) => void;
    submitted: boolean
}) {
    const {pending} = useFormStatus();
    const isSelected = selectedLabel === label;
    const disabled = (pending || submitted);
    return (
        <button
            name="bucketGuess"
            value={label}
            disabled={disabled}
            type="submit"
            className={`review-round__button${isSelected ? ' is-selected' : ''}`}
            onClick={() => onSelect(label)}
        >
            {label}
        </button>
    );
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
                    <ResultView result={(effectiveResponse ?? {
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
                    <FinalSummary
                        buckets={buckets}
                        gameDate={gameDate}
                        totalRounds={totalRounds}
                        latestRound={storedThisRound ? roundIndex : roundIndex}
                        latest={storedThisRound ? storedThisRound : {
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
                            latestRound={storedThisRound ? roundIndex : roundIndex}
                            latest={storedThisRound ? storedThisRound : {
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
            <Form action={formAction} className="review-round__buttons">
                <input type="hidden" name="appId" value={appId}/>
                {buckets.map(label => (
                    <BucketButton key={label} label={label} selectedLabel={selectedLabel} onSelect={setSelectedLabel}
                                  submitted={submittedFlag}/>
                ))}
            </Form>

            {state && !state.ok && state.error && (
                <p className="text-muted review-round__error">Error: {state.error}</p>
            )}

            {effectiveResponse && (
                <div role="dialog" aria-modal="true" className="review-round__result">
                    <ResultView result={effectiveResponse}/>
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
        </>
    );
}

function ResultView({result}: { result: GuessResponse }) {
    return (
        <p>
            {result.correct ? '✅ Correct!' : '❌ Not quite.'} Actual bucket: <strong>{result.actualBucket}</strong> ·
            Reviews: <strong>{result.totalReviews}</strong>
        </p>
    );
}

function scoreForRound(buckets: string[], selectedLabel: string, actual: string): {
    bar: string;
    points: number;
    distance: number
} {
    const selectedIndex = buckets.indexOf(selectedLabel);
    const actualIndex = buckets.indexOf(actual);
    if (selectedIndex < 0 || actualIndex < 0) return {bar: '⬜', points: 0, distance: 0};
    const d = Math.abs(selectedIndex - actualIndex);
    const maxPoints = 5;
    const step = 2;
    const points = Math.max(0, maxPoints - step * d);
    const emojiByDistance = ['🟩', '🟨', '🟧'];
    const bar = d <= 2 ? emojiByDistance[d] : '🟥';
    return {bar, points, distance: d};
}

function RoundPoints({buckets, selectedLabel, actualBucket}: {
    buckets: string[];
    selectedLabel: string | null | undefined;
    actualBucket: string
}) {
    if (!selectedLabel) return null;
    const {bar, points, distance} = scoreForRound(buckets, selectedLabel, actualBucket);
    return (
        <p className="review-round__points">
            <span className="points-emoji" aria-hidden="true">{bar}</span>
            <strong>{points}</strong> {points === 1 ? 'point' : 'points'} — {distance === 0 ? 'exact match' : `off by ${distance}`}
        </p>
    );
}

function FinalSummary(props: {
    buckets: string[];
    gameDate?: string;
    totalRounds: number;
    latestRound: number;
    latest: {
        appId: number;
        pickName?: string;
        selectedLabel: string;
        actualBucket: string;
        totalReviews: number;
        correct: boolean
    }
}) {
    const {buckets, gameDate, totalRounds, latestRound, latest} = props;
    if (!gameDate) return null;

    type RoundResult = {
        pickName?: string;
        appId: number;
        selectedLabel: string;
        actualBucket: string;
        totalReviews: number;
        correct: boolean
    };
    type Stored = { totalRounds: number; results: Record<number, RoundResult> };

    let data: Stored | null = null;
    try {
        const raw = typeof window !== 'undefined' ? window.localStorage.getItem(`review-guesser:${gameDate}`) : null;
        data = raw ? JSON.parse(raw) as Stored : null;
    } catch {
        data = null;
    }
    if (!data || !data.results) return null;

    const indices = new Set(Object.keys(data.results).map(n => parseInt(n, 10)));
    indices.add(latestRound);
    const isComplete = indices.size >= totalRounds;
    if (!isComplete) return null;

    let total = 0;
    const bars: string[] = [];
    for (let i = 1; i <= totalRounds; i++) {
        const r = i === latestRound ? latest : data.results[i];
        if (!r) continue;
        const {bar, points} = scoreForRound(buckets, r.selectedLabel, r.actualBucket);
        total += points;
        bars.push(bar);
    }
    const maxTotal = 5 * totalRounds;

    return (
        <div className="review-round__summary" aria-live="polite">
            <div className="summary-bar">{bars.join('')}</div>
            <div className="summary-points"><strong>Score:</strong> {total}/{maxTotal}</div>
        </div>
    );
}

function ShareControls(props: {
    buckets: string[];
    gameDate?: string;
    totalRounds: number;
    latestRound: number;
    latest: {
        appId: number;
        pickName?: string;
        selectedLabel: string;
        actualBucket: string;
        totalReviews: number;
        correct: boolean
    },
    inline?: boolean
}) {
    const {buckets, gameDate, totalRounds, latestRound, latest, inline} = props;
    const [copied, setCopied] = useState(false);

    if (!gameDate) return null;

    type RoundResult = {
        pickName?: string;
        appId: number;
        selectedLabel: string;
        actualBucket: string;
        totalReviews: number;
        correct: boolean
    };
    type Stored = { totalRounds: number; results: Record<number, RoundResult> };

    let data: Stored | null = null;
    try {
        const raw = typeof window !== 'undefined' ? window.localStorage.getItem(`review-guesser:${gameDate}`) : null;
        data = raw ? JSON.parse(raw) as Stored : null;
    } catch {
        data = null;
    }

    if (!data || !data.results) return null;
    // Account for the just-submitted final round which may not yet be in storage
    const indices = new Set(Object.keys(data.results).map(n => parseInt(n, 10)));
    indices.add(latestRound);
    const isComplete = indices.size >= totalRounds;
    if (!isComplete) return null;

    /**
     * Points formula and emoji mapping
     *
     * Let distance d be the absolute index distance between the user's guess and the actual bucket.
     * Points are computed by the linear decay formula:
     *   points = max(0, maxPoints - step * d)
     * With maxPoints = 5 and step = 2, this yields: d=0 → 5, d=1 → 3, d=2 → 1, d≥3 → 0.
     *
     * Emoji encoding uses the first four symbols for distance buckets, plus a fallback for invalid inputs:
     *   d=0 → 🟩, d=1 → 🟨, d=2 → 🟧, d≥3 → 🟥, invalid → ⬜
     * This keeps visual summaries stable even if the total number of buckets changes.
     */
    const lines: string[] = [];
    lines.push(`https://steam5.org/review-guesser - Steam Review Game — ${gameDate}`);
    let total = 0;
    const bars: string[] = [];
    for (let i = 1; i <= totalRounds; i++) {
        const r = i === latestRound ? latest : data.results[i];
        if (!r) continue;
        const {bar, points, distance} = scoreForRound(buckets, r.selectedLabel, r.actualBucket);
        total += points;
        bars.push(bar);
        lines.push(`${bar} | Round ${i}: ${r.pickName ?? 'App ' + r.appId} — off by ${distance}`);
    }
    if (bars.length > 0) {
        const maxTotal = 5 * totalRounds;
        lines.splice(1, 0, `${bars.join('')} ${total}/${maxTotal}`, '');
    }
    lines.push('');
    lines.push(`Total points: ${total}`);
    const text = lines.join('\n');

    async function copyToClipboard() {
        try {
            await navigator.clipboard.writeText(text);
            setCopied(true);
            setTimeout(() => setCopied(false), 1500);
        } catch {
            /* no-op */
        }
    }

    if (inline) {
        return (
            <div className="share-inline">
                <button className="btn-success" onClick={copyToClipboard}>Share Results</button>
                <span className={`share-copied ${copied ? 'is-visible' : ''}`}>Copied</span>
            </div>
        );
    }
    return (
        <div className="review-round__share">
            <div className="share-inline">
                <button className="btn-success" onClick={copyToClipboard}>Share Results</button>
                <span className={`share-copied ${copied ? 'is-visible' : ''}`}>Copied</span>
            </div>
        </div>
    );
}
