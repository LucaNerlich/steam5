"use client";

import React from "react";
import type {GuessResponse} from "@/types/review-game";
import {ArrowRightIcon} from "@phosphor-icons/react/ssr";
import type {RoundResultTier} from "@/lib/scoring";
import {tierEmoji} from "@/lib/scoring";
import "@/styles/components/reviewRoundResult.css";

export default function RoundResult({result, selectedLabel, actualBucket, headerText, tier}: {
    result: GuessResponse;
    selectedLabel?: string | null;
    actualBucket: string;
    headerText: string;
    tier: RoundResultTier;
}): React.ReactElement {
    const correct = result.correct;
    const showComparison = !!selectedLabel;

    return (
        <div className="result-body">
            <div className={`result-header result-header--${tier}`}
                 aria-live="polite">
                <span className="result-header__icon result-header__icon--square" aria-hidden="true">{tierEmoji(tier)}</span>
                <span className="result-header__text">
                    {headerText}
                </span>
            </div>

            {showComparison && (
                <div className="result-comparison">
                    <div className="result-comparison__col">
                        <span className="result-comparison__label">Your pick</span>
                        <span
                            className={`result-chip result-chip--${tier}`}
                            data-mobile-label="Your pick"
                        >
                            {selectedLabel}
                        </span>
                    </div>
                    <ArrowRightIcon size={16} className="result-comparison__arrow" aria-hidden="true"/>
                    <div className="result-comparison__col">
                        <span className="result-comparison__label">Actual</span>
                        <span className="result-chip result-chip--actual" data-mobile-label="Actual">
                            {actualBucket}
                        </span>
                    </div>
                </div>
            )}
        </div>
    );
}
