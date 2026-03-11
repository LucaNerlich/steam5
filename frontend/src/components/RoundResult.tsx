"use client";

import React from "react";
import type {GuessResponse} from "@/types/review-game";
import {CheckCircleIcon, XCircleIcon, ArrowRightIcon} from "@phosphor-icons/react/ssr";
import "@/styles/components/reviewRoundResult.css";

export default function RoundResult({result, selectedLabel, actualBucket}: {
    result: GuessResponse;
    selectedLabel?: string | null;
    actualBucket: string;
}): React.ReactElement {
    const correct = result.correct;
    const showComparison = !!selectedLabel;

    return (
        <div className="result-body">
            <div className={`result-header ${correct ? 'result-header--correct' : 'result-header--incorrect'}`}
                 aria-live="polite">
                {correct
                    ? <CheckCircleIcon size={22} weight="fill" className="result-header__icon"/>
                    : <XCircleIcon size={22} weight="fill" className="result-header__icon"/>
                }
                <span className="result-header__text">
                    {correct ? 'Correct!' : 'Not quite.'}
                </span>
            </div>

            {showComparison && (
                <div className="result-comparison">
                    <div className="result-comparison__col">
                        <span className="result-comparison__label">Your pick</span>
                        <span className={`result-chip ${correct ? 'result-chip--correct' : 'result-chip--incorrect'}`}>
                            {selectedLabel}
                        </span>
                    </div>
                    <ArrowRightIcon size={16} className="result-comparison__arrow" aria-hidden="true"/>
                    <div className="result-comparison__col">
                        <span className="result-comparison__label">Actual</span>
                        <span className="result-chip result-chip--actual">
                            {actualBucket}
                        </span>
                    </div>
                </div>
            )}
        </div>
    );
}
