"use client";

import {useCallback, useMemo, useState} from "react";
import type {GuessRequest, GuessResponse} from "@/types/review-game";
import Link from "next/link";

interface Props {
    appId: number;
    buckets: string[];
    roundIndex: number;
    totalRounds: number;
}

export default function ReviewGuesserRound({appId, buckets, roundIndex, totalRounds}: Props) {
    const [isSubmitting, setSubmitting] = useState(false);
    const [result, setResult] = useState<GuessResponse | null>(null);
    const [error, setError] = useState<string | null>(null);

    const nextHref = useMemo(() => {
        const next = roundIndex + 1;
        return next <= totalRounds ? `/review-guesser/${next}` : `/review-guesser/1`;
    }, [roundIndex, totalRounds]);

    const submitGuess = useCallback(async (bucketGuess: string) => {
        setSubmitting(true);
        setError(null);
        try {
            const body: GuessRequest = {appId, bucketGuess};
            const res = await fetch(`/api/review-game/guess`, {
                method: 'POST',
                headers: {"content-type": "application/json", "accept": "application/json"},
                body: JSON.stringify(body)
            });
            if (!res.ok) {
                throw new Error(`Request failed: ${res.status}`);
            }
            const json: GuessResponse = await res.json();
            setResult(json);
        } catch (err: unknown) {
            setError(err instanceof Error ? err.message : 'Unknown error');
        } finally {
            setSubmitting(false);
        }
    }, [appId]);

    return (
        <div>
            <div style={{display: 'flex', gap: '8px', flexWrap: 'wrap'}}>
                {buckets.map(label => (
                    <button key={label} disabled={isSubmitting} onClick={() => submitGuess(label)}>
                        {label}
                    </button>
                ))}
            </div>

            {error && (
                <p className="text-muted" style={{marginTop: '8px'}}>Error: {error}</p>
            )}

            {result && (
                <div role="dialog" aria-modal="true" style={{
                    marginTop: '16px',
                    border: '1px solid var(--color-border)',
                    padding: '12px',
                    background: 'var(--color-surface)'
                }}>
                    <p>
                        {result.correct ? '✅ Correct!' : '❌ Not quite.'} Actual
                        bucket: <strong>{result.actualBucket}</strong> · Reviews: <strong>{result.totalReviews}</strong>
                    </p>
                    <div style={{marginTop: '8px'}}>
                        <Link href={nextHref}>Next round</Link>
                    </div>
                </div>
            )}
        </div>
    );
}


