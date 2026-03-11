"use client";

import {ReactNode} from "react";
import type {GuessResponse} from "@/types/review-game";
import RoundResult from "@/components/RoundResult";
import RoundPoints from "@/components/RoundPoints";

const fmt = new Intl.NumberFormat();

export default function RoundResultDialog(props: {
    buckets: string[];
    selectedLabel: string | null | undefined;
    result: GuessResponse;
    children?: ReactNode;
}) {
    const correct = props.result.correct;
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
            />
            <div className="result-footer">
                <span className="result-detail">
                    {fmt.format(props.result.totalReviews)} total reviews
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
