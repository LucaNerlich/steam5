"use client";

import React from "react";
import type {GuessResponse} from "@/types/review-game";
import "@/styles/components/reviewRoundResult.css";

export default function RoundResult({result, selectedLabel}: {
    result: GuessResponse;
    selectedLabel?: string | null
}): React.ReactElement {
    return (
        <p>
            {result.correct ? '✅ Correct!' : '❌ Not quite.'} ·
            Reviews: <strong>{result.totalReviews}</strong>
            {selectedLabel ? (
                <>
                    {' '}· Your pick: <strong>{selectedLabel}</strong>
                </>
            ) : null}
        </p>
    );
}


