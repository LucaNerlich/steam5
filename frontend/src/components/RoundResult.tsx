"use client";

import React from "react";
import type {GuessResponse} from "@/types/review-game";
import "@/styles/components/reviewRoundResult.css";

export default function RoundResult({result}: { result: GuessResponse }): React.ReactElement {
    return (
        <p>
            {result.correct ? '✅ Correct!' : '❌ Not quite.'} Actual bucket: <strong>{result.actualBucket}</strong> ·
            Reviews: <strong>{result.totalReviews}</strong>
        </p>
    );
}


