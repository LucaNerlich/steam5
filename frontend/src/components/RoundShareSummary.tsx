"use client";

import ShareControls from "@/components/ShareControls";
import RoundSummary from "@/components/RoundSummary";
import type {RoundResult as StoredRoundResult} from "@/lib/storage";

export default function RoundShareSummary(props: {
    buckets: string[];
    gameDate: string | undefined;
    totalRounds: number;
    latestRound: number;
    latest: StoredRoundResult;
    results?: Record<number, StoredRoundResult>;
    signedIn: boolean | null;
}) {
    const canShowShare = Boolean(props.results && Object.keys(props.results).length > 0) || Boolean(props.latest);

    return (
        <>
            {canShowShare && (
                <ShareControls
                    inline
                    buckets={props.buckets}
                    gameDate={props.gameDate}
                    totalRounds={props.totalRounds}
                    latestRound={props.latestRound}
                    latest={props.latest}
                    results={props.results}
                />
            )}
            {canShowShare && (
                <RoundSummary
                    buckets={props.buckets}
                    gameDate={props.gameDate}
                    totalRounds={props.totalRounds}
                    latestRound={props.latestRound}
                    latest={props.latest}
                    results={props.results}
                />
            )}
        </>
    );
}


