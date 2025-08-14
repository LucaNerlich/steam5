"use client";

import {useActionState, useMemo} from "react";
import type {GuessResponse} from "@/types/review-game";
import Link from "next/link";
import Form from "next/form";
import {useFormStatus} from "react-dom";
import type {GuessActionState} from "../../app/review-guesser/[round]/actions";
import {submitGuessAction} from "../../app/review-guesser/[round]/actions";
import "@/styles/reviewGuesserRound.css";

interface Props {
    appId: number;
    buckets: string[];
    roundIndex: number;
    totalRounds: number;
}

function BucketButton({label}: { label: string }) {
    const {pending} = useFormStatus();
    return (
        <button name="bucketGuess" value={label} disabled={pending} type="submit">
            {label}
        </button>
    );
}

export default function ReviewGuesserRound({appId, buckets, roundIndex, totalRounds}: Props) {
    const initial: GuessActionState = {ok: false};
    const [state, formAction] = useActionState<GuessActionState, FormData>(submitGuessAction, initial);

    const nextHref = useMemo(() => {
        const next = roundIndex + 1;
        return next <= totalRounds ? `/review-guesser/${next}` : `/review-guesser/1`;
    }, [roundIndex, totalRounds]);

    return (
        <div>
            <Form action={formAction} className="review-round__buttons">
                <input type="hidden" name="appId" value={appId}/>
                {buckets.map(label => (
                    <BucketButton key={label} label={label}/>
                ))}
            </Form>

            {state && !state.ok && state.error && (
                <p className="text-muted review-round__error">Error: {state.error}</p>
            )}

            {state && state.ok && state.response && (
                <div role="dialog" aria-modal="true" className="review-round__result">
                    <ResultView result={state.response}/>
                    <div className="review-round__next">
                        <Link href={nextHref}>Next round</Link>
                    </div>
                </div>
            )}
        </div>
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


