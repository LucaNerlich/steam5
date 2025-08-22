"use client";

import React, {useCallback, useEffect, useMemo, useState} from "react";
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

    const [selectedLabel, setSelectedLabel] = useState<string | null>(null);
    const [submitted, setSubmitted] = useState<boolean>(false);
    const [stored, setStored] = useState<StoredDay | null>(null);

    // Prev/next anchors within the archive page
    const prevHref = useMemo(() => (roundIndex > 1 ? `#round-${roundIndex - 1}` : null), [roundIndex]);
    const nextHref = useMemo(() => (roundIndex < totalRounds ? `#round-${roundIndex + 1}` : null), [roundIndex, totalRounds]);

    // Build a local GuessResponse when a choice is made
    const localResponse: GuessResponse | null = useMemo(() => {
        if (!offlineAnswer || !selectedLabel) return null;
        return {
            appId,
            totalReviews: offlineAnswer.totalReviews,
            actualBucket: offlineAnswer.actualBucket,
            correct: offlineAnswer.actualBucket === selectedLabel,
        } as GuessResponse;
    }, [appId, offlineAnswer, selectedLabel]);

    // Restore from local storage if already answered
    useEffect(() => {
        const d = loadDay(gameDate);
        setStored(d);
        const prev = d?.results?.[roundIndex];
        if (prev && prev.selectedLabel) {
            setSelectedLabel(prev.selectedLabel);
            setSubmitted(true);
        }
    }, [gameDate, roundIndex]);

    // Persist this round's result when available
    useEffect(() => {
        if (!localResponse || !selectedLabel) return;
        const updated = saveRound(gameDate, roundIndex, totalRounds, {
            appId,
            pickName,
            selectedLabel,
            actualBucket: localResponse.actualBucket,
            totalReviews: localResponse.totalReviews,
            correct: localResponse.correct,
        });
        if (updated) setStored(updated);
        setSubmitted(true);
    }, [localResponse, selectedLabel, gameDate, roundIndex, totalRounds, appId, pickName]);

    const onSelect = useCallback((label: string) => {
        if (submitted) return;
        setSelectedLabel(label);
    }, [submitted]);

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


