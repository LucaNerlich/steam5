"use client";

import React, {useCallback, useEffect, useMemo, useState} from "react";
import Link from "next/link";
import RoundResultDialog from "@/components/RoundResultDialog";
import RoundResultActions from "@/components/RoundResultActions";
import type {GuessResponse} from "@/types/review-game";
import {loadDay, saveRound, type StoredDay} from "@/lib/storage";
import {QuestionIcon} from "@phosphor-icons/react/ssr";
import {Routes} from "../../app/routes";
import "@/styles/components/reviewGuesserRound.css";
import "@/styles/components/reviewRoundResult.css";
import "@/styles/components/reviewGuessButtons.css";

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

    // Initialize state empty so the server render and the first client render match;
    // persisted local progress is loaded after mount to avoid a hydration mismatch.
    const [selectedLabel, setSelectedLabel] = useState<string | null>(null);
    const [submitted, setSubmitted] = useState<boolean>(false);
    const [stored, setStored] = useState<StoredDay | null>(null);

    useEffect(() => {
        const d = loadDay(gameDate);
        if (!d) return;
        setStored(d);
        const existing = d.results?.[roundIndex];
        if (existing?.selectedLabel) {
            setSelectedLabel(existing.selectedLabel);
            setSubmitted(true);
        }
    }, [gameDate, roundIndex]);

    const prevHref = roundIndex > 1 ? `#round-${roundIndex - 1}` : null;
    const nextHref = roundIndex < totalRounds ? `#round-${roundIndex + 1}` : null;
    const randomArchiveHref = roundIndex >= totalRounds ? Routes.randomArchive : null;

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
                <section className="review-round__guess-card" aria-labelledby={`guess-submission-${roundIndex}`}>
                    <div className="review-round__guess-header">
                        <h2 id={`guess-submission-${roundIndex}`}>Submit Your Guess (Offline Play)</h2>
                    </div>
                    <div className="review-round__guess-body">
                        <p className="review-round__guess-helper">
                            Pick the review bucket that best matches this game.{' '}
                            <Link href={Routes.howToPlay} className="review-round__how-to-link">
                                <QuestionIcon size={14} weight="bold"/> How to play?
                            </Link>
                        </p>
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
                    </div>
                    {!offlineAnswer && (
                        <p className="text-muted review-round__error">Offline answer unavailable, try reloading
                            later.</p>
                    )}
                </section>
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
                    <RoundResultActions
                        appId={appId}
                        prevHref={prevHref}
                        nextHref={nextHref}
                        randomArchiveHref={randomArchiveHref}
                    />
                </RoundResultDialog>
            )}
        </div>
    );
}


