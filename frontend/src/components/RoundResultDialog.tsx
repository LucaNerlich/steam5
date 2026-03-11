"use client";

import {ReactNode} from "react";
import type {GuessResponse} from "@/types/review-game";
import RoundResult from "@/components/RoundResult";
import RoundPoints from "@/components/RoundPoints";
import {scoreForRound} from "@/lib/scoring";

const fmt = new Intl.NumberFormat();

function resolveHeaderText(correct: boolean, buckets: string[], selectedLabel: string | null | undefined, actualBucket: string): string {
    if (correct) return 'Hit!';
    if (selectedLabel) {
        const {points} = scoreForRound(buckets, selectedLabel, actualBucket);
        if (points === 0) return 'Flop!';
    }
    return 'Not quite.';
}

export default function RoundResultDialog(props: {
    buckets: string[];
    selectedLabel: string | null | undefined;
    result: GuessResponse;
    children?: ReactNode;
}) {
    const correct = props.result.correct;
    const headerText = resolveHeaderText(correct, props.buckets, props.selectedLabel, props.result.actualBucket);
    return (
        <div
            role="dialog"
            aria-modal="true"
            className={`review-round__result ${correct ? 'review-round__result--correct' : 'review-round__result--incorrect'}`}
        >
            <RoundResult
                result={props.result}
                selectedLabel={props.selectedLabel ?? null}
                actualBucket={props.result.actualBucket}
                headerText={headerText}
            />
            <div className="result-footer">
                <span className="result-detail">
                    Reviews Σ={fmt.format(props.result.totalReviews)}
                </span>
                <RoundPoints
                    buckets={props.buckets}
                    selectedLabel={props.selectedLabel}
                    actualBucket={props.result.actualBucket}
                />
            </div>
            {props.children}
        </div>
    );
}
