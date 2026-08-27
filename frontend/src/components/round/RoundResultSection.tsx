"use client";

import OtherPlayersNow from "@/components/OtherPlayersNow";
import RoundResultDialog from "@/components/RoundResultDialog";
import RoundResultActions from "@/components/RoundResultActions";
import ShareControls from "@/components/ShareControls";
import RoundSummary from "@/components/RoundSummary";
import {buildSteamLoginUrl} from "@/lib/steamLogin";
import type {GuessResponse} from "@/types/review-game";
import type {RoundResult} from "@/lib/storage";

type RoundResultSectionProps = {
    appId: number;
    buckets: string[];
    selectedLabel: string | null;
    result: GuessResponse;
    prevHref: string | null;
    /** Next round link; null after the last round. */
    nextHref: string | null;
    /** Random-archive link; null before the last round. */
    randomArchiveHref: string | null;
    canShowShare: boolean;
    gameDate?: string;
    totalRounds: number;
    latestRound: number;
    latest: RoundResult;
    /** Full per-round results when available (server-provided); else undefined. */
    shareResults?: Record<number, RoundResult>;
    signedIn: boolean | null;
    /** True when the session was lost mid-round (result did not count). */
    signedOutDuringPlay: boolean;
};

/**
 * The round-result view: result dialog with navigation actions, share/summary
 * controls, and the sign-in nudges shown when a result was not saved.
 */
export default function RoundResultSection({
                                               appId,
                                               buckets,
                                               selectedLabel,
                                               result,
                                               prevHref,
                                               nextHref,
                                               randomArchiveHref,
                                               canShowShare,
                                               gameDate,
                                               totalRounds,
                                               latestRound,
                                               latest,
                                               shareResults,
                                               signedIn,
                                               signedOutDuringPlay
                                           }: RoundResultSectionProps) {
    return (
        <RoundResultDialog
            buckets={buckets}
            selectedLabel={selectedLabel}
            result={result}
            headerRight={<OtherPlayersNow/>}
        >
            <RoundResultActions
                appId={appId}
                prevHref={prevHref}
                nextHref={nextHref}
                randomArchiveHref={randomArchiveHref}
            >
                {canShowShare && (
                    <>
                        <ShareControls
                            inline
                            buckets={buckets}
                            gameDate={gameDate}
                            totalRounds={totalRounds}
                            latestRound={latestRound}
                            latest={latest}
                            results={shareResults}
                            signedIn={signedIn}
                        />
                        <RoundSummary
                            buckets={buckets}
                            gameDate={gameDate}
                            totalRounds={totalRounds}
                            latestRound={latestRound}
                            latest={latest}
                            results={shareResults}
                        />
                        {signedOutDuringPlay ? (
                            <p className="text-muted review-round__signin-nudge">
                                You&apos;ve been signed out, so this result wasn&apos;t saved.{" "}
                                <button type="button" className="btn-link" onClick={() => {
                                    window.location.href = buildSteamLoginUrl();
                                }}>Sign in with Steam</button>
                                &nbsp;to save your results, track streaks, and appear on the leaderboard.
                            </p>
                        ) : signedIn === false && (
                            <p className="text-muted review-round__signin-nudge">
                                <button type="button" className="btn-link" onClick={() => {
                                    window.location.href = buildSteamLoginUrl();
                                }}>Sign in with Steam</button>
                                &nbsp;to save your results, track streaks, and appear on the leaderboard.
                            </p>
                        )}
                    </>
                )}
            </RoundResultActions>
        </RoundResultDialog>
    );
}
