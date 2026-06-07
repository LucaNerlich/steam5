"use client";

import React from "react";
import {scoreForRound} from "@/lib/scoring";
import "@/styles/components/reviewRoundPoints.css";

const POINTS_PLURAL_RULES = new Intl.PluralRules("en-US");

export default function RoundPoints({buckets, selectedLabel, actualBucket}: {
    buckets: string[];
    selectedLabel: string | null | undefined;
    actualBucket: string
}) {
    if (!selectedLabel) return null;
    const {points} = scoreForRound(buckets, selectedLabel, actualBucket);
    const pointsLabel = POINTS_PLURAL_RULES.select(points) === "one" ? "point" : "points";

    return (
        <div className="result-points">
            <div className="result-points__text">
                <strong>{points}</strong> {pointsLabel}
            </div>
        </div>
    );
}
