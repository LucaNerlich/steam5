"use client";

import {ReactNode} from "react";
import type {GuessResponse} from "@/types/review-game";
import RoundResult from "@/components/RoundResult";
import RoundPoints from "@/components/RoundPoints";

export default function RoundResultDialog(props: {
    buckets: string[];
    selectedLabel: string | null | undefined;
    result: GuessResponse;
    children?: ReactNode;
}) {
    return (
        <div role="dialog" aria-modal="true" className="review-round__result">
            <RoundResult
                result={props.result}
                selectedLabel={props.selectedLabel ?? null}
            />
            <RoundPoints
                buckets={props.buckets}
                selectedLabel={props.selectedLabel}
                actualBucket={props.result.actualBucket}
            />
            {props.children}
        </div>
    );
}


