"use client";

import type {ReactNode} from "react";
import OtherPlayersNow from "@/components/OtherPlayersNow";
import {ArrowRightIcon} from "@phosphor-icons/react/ssr";
import "@/styles/components/reviewRoundResult.css";
import "@/styles/components/reviewRoundPoints.css";

const POINTS_PLURAL_RULES = new Intl.PluralRules("en-US");

interface YearRoundResultDialogProps {
    guessYear: number;
    actualYear: number;
    hintsUsed: number;
    points: number;
    maxPoints: number;
    children?: ReactNode;
}

export default function YearRoundResultDialog({
    guessYear,
    actualYear,
    hintsUsed,
    points,
    maxPoints,
    children,
}: YearRoundResultDialogProps): React.ReactElement {
    const pointsLabel = POINTS_PLURAL_RULES.select(points) === "one" ? "point" : "points";
    const hintsDetail = hintsUsed > 0
        ? `${hintsUsed} hint${hintsUsed === 1 ? "" : "s"} used`
        : "No hints used";

    return (
        <div
            role="dialog"
            aria-modal="true"
            className="review-round__result review-round__result--exact"
        >
            <div className="result-body">
                <div className="result-header result-header--exact" aria-live="polite">
                    <div className="result-header__left">
                        <span className="result-header__icon result-header__icon--square" aria-hidden="true">🎯</span>
                        <span className="result-header__text">Hit!</span>
                    </div>
                    <OtherPlayersNow/>
                </div>

                <div className="result-comparison">
                    <div className="result-comparison__col">
                        <span className="result-comparison__label">Your guess</span>
                        <span className="result-chip result-chip--exact" data-mobile-label="Your guess">
                            {guessYear}
                        </span>
                    </div>
                    <ArrowRightIcon size={16} className="result-comparison__arrow" aria-hidden="true"/>
                    <div className="result-comparison__col">
                        <span className="result-comparison__label">Actual</span>
                        <span className="result-chip result-chip--actual" data-mobile-label="Actual">
                            {actualYear}
                        </span>
                    </div>
                </div>

                <div className="result-footer">
                    <span className="result-detail">{hintsDetail} · max {maxPoints} pts</span>
                    <div className="result-points">
                        <div className="result-points__text">
                            <strong>{points}</strong> {pointsLabel}
                        </div>
                    </div>
                </div>
            </div>
            {children}
        </div>
    );
}
