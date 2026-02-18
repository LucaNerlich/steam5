"use client";

import React, {useCallback, useMemo, useState} from "react";
import RoundResultDialog from "@/components/RoundResultDialog";
import RoundResultActions from "@/components/RoundResultActions";
import type {GuessResponse} from "@/types/review-game";
import {loadDay, saveRound, type StoredDay} from "@/lib/storage";
import "@/styles/components/reviewGuesserRound.css";
import "@/styles/components/reviewRoundResult.css";

type Props = {
    appId: number;
    buckets: string[];
    bucketTitles?: string[];
    roundIndex: number;
    totalRounds: number;
    pickName?: string;
    gameDate: string;
    offlineAnswer: { actualBucket: string; totalReviews: number } | null;
};

export default function ArchiveOfflineRound(props: Readonly<Props>): React.ReactElement {
    const {appId, buckets, bucketTitles, roundIndex, totalRounds, pickName, gameDate, offlineAnswer} = props;

    const [selectedLabel, setSelectedLabel] = useState<string | null>(() => {
        const d = loadDay(gameDate);
        return d?.results?.[roundIndex]?.selectedLabel ?? null;
    });
    const [submitted, setSubmitted] = useState<boolean>(() => {
        const d = loadDay(gameDate);
        return Boolean(d?.results?.[roundIndex]?.selectedLabel);
    });
    const [stored, setStored] = useState<StoredDay | null>(() => loadDay(gameDate));

    const prevHref = roundIndex > 1 ? `#round-${roundIndex - 1}` : null;
    const nextHref = roundIndex < totalRounds ? `#round-${roundIndex + 1}` : null;

    const localResponse: GuessResponse | null = useMemo(() => {
        if (!offlineAnswer || !selectedLabel) return null;
        return {
            appId,
            totalReviews: offlineAnswer.totalReviews,
            actualBucket: offlineAnswer.actualBucket,
            correct: offlineAnswer.actualBucket === selectedLabel,
        } as GuessResponse;
    }, [appId, offlineAnswer, selectedLabel]);

    const onSelect = useCallback((label: string) => {
        if (submitted) return;
        setSelectedLabel(label);
        if (!offlineAnswer) return;
        const result = {
            appId,
            pickName,
            selectedLabel: label,
            actualBucket: offlineAnswer.actualBucket,
            totalReviews: offlineAnswer.totalReviews,
            correct: offlineAnswer.actualBucket === label,
        };
        const updated = saveRound(gameDate, roundIndex, totalRounds, result);
        if (updated) setStored(updated);
        setSubmitted(true);
    }, [submitted, offlineAnswer, appId, pickName, gameDate, roundIndex, totalRounds]);

    return (
        <div className="archive-offline-round">
            {!submitted && (
                <>
                    <h2 id={`guess-submission-${roundIndex}`}>Submit Your Guess (Offline Play)</h2>
                    <div className="review-round__buttons">
                        {buckets.map((label, i) => (
                            <button
                                key={label}
                                type="button"
                                className={`review-round__button${selectedLabel === label ? ' is-selected' : ''}`}
                                onClick={() => onSelect(label)}
                                title={(bucketTitles && bucketTitles[i] ? bucketTitles[i] : undefined)}
                                disabled={!offlineAnswer}
                            >
                                {label}
                            </button>
                        ))}
                    </div>
                    {!offlineAnswer && (
                        <p className="text-muted review-round__error">Offline answer unavailable, try reloading
                            later.</p>
                    )}
                </>
            )}

            {(localResponse || (stored?.results?.[roundIndex] && submitted)) && (
                <RoundResultDialog
                    buckets={buckets}
                    selectedLabel={selectedLabel}
                    result={(localResponse || {
                        appId,
                        totalReviews: stored?.results?.[roundIndex]?.totalReviews ?? 0,
                        actualBucket: stored?.results?.[roundIndex]?.actualBucket ?? (offlineAnswer?.actualBucket ?? ""),
                        correct: stored?.results?.[roundIndex]?.correct ?? false,
                    }) as GuessResponse}
                >
                    <RoundResultActions appId={appId} prevHref={prevHref} nextHref={nextHref}/>
                </RoundResultDialog>
            )}
        </div>
    );
}


